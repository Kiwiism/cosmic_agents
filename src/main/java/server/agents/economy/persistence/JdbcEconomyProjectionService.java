package server.agents.economy.persistence;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/** Rebuilds disposable dashboard read models exclusively from immutable evidence and postings. */
public final class JdbcEconomyProjectionService {
    private final DataSource dataSource;

    public JdbcEconomyProjectionService(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    public Result rebuild(UUID runId) {
        Objects.requireNonNull(runId);
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit(); connection.setAutoCommit(false);
            try {
                delete(connection, "item_market_daily", runId);
                delete(connection, "meso_flow_daily", runId);
                delete(connection, "agent_state_projection", runId);
                delete(connection, "listing_exposure", runId);
                int items = execute(connection, ITEM_DAILY, runId, 2);
                int flows = execute(connection, MESO_DAILY, runId, 1);
                int exposures = execute(connection, LISTING_EXPOSURE, runId, 1);
                int agents = execute(connection, AGENT_STATE, runId, 1);
                connection.commit();
                return new Result(items, flows, exposures, agents);
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally { connection.setAutoCommit(autoCommit); }
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not rebuild economy projections", failure);
        }
    }

    private static void delete(Connection connection, String table, UUID runId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE run_id = ?")) {
            statement.setObject(1, runId); statement.executeUpdate();
        }
    }

    private static int execute(Connection connection, String sql, UUID runId, int parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 1; index <= parameters; index++) statement.setObject(index, runId);
            return statement.executeUpdate();
        }
    }

    private static final String ITEM_DAILY = """
            INSERT INTO item_market_daily (run_id, logical_date, item_id, completed_quantity,
                completed_trade_count, meso_volume, vwap, minimum_price, maximum_price,
                npc_created_quantity, farm_created_quantity, quest_created_quantity,
                transformed_created_quantity, npc_destroyed_quantity, consumed_quantity)
            WITH item_flow AS (
                SELECT e.run_id, e.logical_time::date logical_date, p.asset_identifier::integer item_id,
                    SUM(CASE WHEN e.event_kind IN ('STALL_SALE','DIRECT_TRADE')
                        AND p.account_type IN ('AGENT','HUMAN') AND p.quantity > 0 THEN p.quantity ELSE 0 END) completed_quantity,
                    COUNT(DISTINCT e.event_id) FILTER (WHERE e.event_kind IN ('STALL_SALE','DIRECT_TRADE')) completed_trade_count,
                    -SUM(CASE WHEN p.account_type = 'SOURCE'
                        AND (p.account_owner_id LIKE 'NPC_STOCK:%' OR p.account_owner_id LIKE 'NPC_RECHARGE:%')
                        AND p.quantity < 0 THEN p.quantity ELSE 0 END) npc_created_quantity,
                    -SUM(CASE WHEN p.account_type = 'SOURCE' AND p.account_owner_id LIKE 'MOB:%'
                        AND p.quantity < 0 THEN p.quantity ELSE 0 END) farm_created_quantity,
                    -SUM(CASE WHEN p.account_type = 'SOURCE' AND p.account_owner_id LIKE 'QUEST:%'
                        AND p.quantity < 0 THEN p.quantity ELSE 0 END) quest_created_quantity,
                    -SUM(CASE WHEN p.account_type = 'SOURCE'
                        AND p.account_owner_id LIKE 'SCROLL_TRANSFORMATION:%'
                        AND p.quantity < 0 THEN p.quantity ELSE 0 END) transformed_created_quantity,
                    SUM(CASE WHEN p.account_type = 'SINK' AND p.account_owner_id LIKE 'NPC_BUYBACK:%'
                        AND p.quantity > 0 THEN p.quantity ELSE 0 END) npc_destroyed_quantity,
                    SUM(CASE WHEN p.account_type = 'SINK' AND p.quantity > 0 AND (
                            p.account_owner_id = 'FARM_CONSUMPTION'
                            OR p.account_owner_id = 'SCROLL_CONSUMPTION'
                            OR p.account_owner_id = 'DEATH_SAFETY_CHARM'
                            OR p.account_owner_id LIKE 'QUEST_REQUIREMENT:%'
                            OR (p.account_owner_id = 'SCROLL_INPUT'
                                AND e.evidence->'scrollApplication'->>'outcome' = 'CURSE'))
                        THEN p.quantity ELSE 0 END) consumed_quantity
                FROM economic_event e JOIN ledger_posting p ON p.event_id = e.event_id
                WHERE e.run_id = ? AND p.asset_type = 'ITEM'
                GROUP BY e.run_id, e.logical_time::date, p.asset_identifier::integer
            ), priced AS (
                SELECT run_id, logical_at::date logical_date, item_id, SUM(gross_mesos) meso_volume,
                    SUM(gross_mesos)::numeric / NULLIF(SUM(quantity), 0) vwap,
                    MIN(gross_mesos / NULLIF(quantity, 0)) minimum_price,
                    MAX(gross_mesos / NULLIF(quantity, 0)) maximum_price
                FROM economic_transaction
                WHERE run_id = ? AND transaction_kind = 'PLAYER_SHOP_SALE'
                    AND item_id IS NOT NULL AND quantity > 0 AND gross_mesos IS NOT NULL
                GROUP BY run_id, logical_at::date, item_id
            )
            SELECT f.run_id, f.logical_date, f.item_id, f.completed_quantity, f.completed_trade_count,
                COALESCE(p.meso_volume, 0), p.vwap, p.minimum_price, p.maximum_price,
                f.npc_created_quantity, f.farm_created_quantity, f.quest_created_quantity,
                f.transformed_created_quantity, f.npc_destroyed_quantity, f.consumed_quantity
            FROM item_flow f LEFT JOIN priced p USING (run_id, logical_date, item_id)
            """;

    private static final String MESO_DAILY = """
            INSERT INTO meso_flow_daily(run_id, logical_date, flow_kind, meso_amount, transaction_count)
            SELECT e.run_id, e.logical_time::date,
                CASE WHEN p.account_type = 'SOURCE' THEN 'CREATED:' || p.account_owner_id
                     ELSE 'DESTROYED:' || p.account_owner_id END,
                SUM(ABS(p.quantity)), COUNT(DISTINCT e.event_id)
            FROM economic_event e JOIN ledger_posting p ON p.event_id = e.event_id
            WHERE e.run_id = ? AND p.asset_type = 'MESO' AND p.account_type IN ('SOURCE','SINK')
            GROUP BY e.run_id, e.logical_time::date, p.account_type, p.account_owner_id
            """;

    private static final String AGENT_STATE = """
            INSERT INTO agent_state_projection(run_id, agent_id, logical_time, level, experience, meso,
                map_id, activity_state, stall_id, needs, beliefs)
            SELECT p.run_id, p.agent_id, l.logical_at,
                COALESCE((progress.evidence->>'levelAfter')::integer,
                         (baseline.evidence->>'level')::integer, 1),
                COALESCE((progress.evidence->>'experienceAfter')::bigint,
                         (baseline.evidence->>'experience')::bigint, 0),
                COALESCE(wallet.meso, 0),
                COALESCE(CASE WHEN l.state = 'OFFSCREEN_ACTIVITY' THEN activity.map_id END,
                         presence.map_id, 910000000),
                l.state, stall.stall_id,
                COALESCE(decision.needs_used, needs.value, '{}'::jsonb),
                COALESCE(decision.beliefs_used, beliefs.value, '{}'::jsonb)
            FROM agent_economic_profile p
            JOIN agent_lifecycle_state l USING (run_id, agent_id)
            LEFT JOIN activity_session activity ON activity.run_id = l.run_id AND activity.activity_id = l.activity_id
            LEFT JOIN LATERAL (SELECT SUM(lp.quantity) meso FROM ledger_posting lp
                JOIN economic_event e USING (event_id) WHERE e.run_id = p.run_id
                AND lp.account_type = 'AGENT' AND lp.account_owner_id = p.agent_id
                AND lp.asset_type = 'MESO') wallet ON true
            LEFT JOIN LATERAL (SELECT e.evidence FROM economic_event e WHERE e.run_id = p.run_id
                AND e.event_kind = 'INITIAL_ENDOWMENT' AND e.actor_ids @> jsonb_build_array(p.agent_id)
                ORDER BY e.logical_time DESC LIMIT 1) baseline ON true
            LEFT JOIN LATERAL (SELECT e.evidence FROM economic_event e WHERE e.run_id = p.run_id
                AND e.evidence ?? 'levelAfter' AND e.actor_ids @> jsonb_build_array(p.agent_id)
                ORDER BY e.logical_time DESC LIMIT 1) progress ON true
            LEFT JOIN LATERAL (SELECT pe.map_id FROM agent_presence_event pe WHERE pe.run_id = p.run_id
                AND pe.agent_id = p.agent_id ORDER BY pe.logical_at DESC LIMIT 1) presence ON true
            LEFT JOIN LATERAL (SELECT s.stall_id FROM market_stall s WHERE s.run_id = p.run_id
                AND s.seller_id = p.agent_id AND s.closed_at IS NULL LIMIT 1) stall ON true
            LEFT JOIN LATERAL (SELECT d.needs_used, d.beliefs_used FROM decision_journal d
                WHERE d.run_id = p.run_id AND d.agent_id = p.agent_id
                ORDER BY d.logical_time DESC, d.decision_id DESC LIMIT 1) decision ON true
            LEFT JOIN LATERAL (SELECT jsonb_object_agg(d.demand_id, to_jsonb(d)) value FROM agent_demand d
                WHERE d.run_id = p.run_id AND d.agent_id = p.agent_id AND d.status = 'OPEN') needs ON true
            LEFT JOIN LATERAL (SELECT jsonb_object_agg(b.item_id::text, to_jsonb(b)) value FROM market_belief b
                WHERE b.run_id = p.run_id AND b.agent_id = p.agent_id) beliefs ON true
            WHERE p.run_id = ?
            """;

    private static final String LISTING_EXPOSURE = """
            INSERT INTO listing_exposure(run_id, listing_id, observer_id, first_seen_at, last_seen_at,
                observation_count)
            SELECT o.run_id, o.listing_id, o.agent_id, MIN(o.logical_time), MAX(o.logical_time), COUNT(*)
            FROM market_observation o JOIN market_listing l
                ON l.run_id = o.run_id AND l.listing_id = o.listing_id
            WHERE o.run_id = ? GROUP BY o.run_id, o.listing_id, o.agent_id
            """;

    public record Result(int itemDailyRows, int mesoFlowRows, int listingExposureRows, int agentRows) { }
}
