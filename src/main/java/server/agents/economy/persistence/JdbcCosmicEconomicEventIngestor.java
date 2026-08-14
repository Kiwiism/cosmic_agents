package server.agents.economy.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.economy.domain.*;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Atomically promotes attributed Cosmic receipts into the canonical PostgreSQL ledger. */
public final class JdbcCosmicEconomicEventIngestor {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DataSource dataSource;

    public JdbcCosmicEconomicEventIngestor(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    public Result ingest(int limit) {
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        List<CosmicOutboxRecord> pending = pending(limit);
        int ingested = 0;
        for (CosmicOutboxRecord receipt : pending) {
            try {
                if (ingestOne(receipt)) ingested++;
            } catch (RuntimeException failure) {
                quarantine(receipt, failure);
                return new Result(ingested, 1, receipt.outboxId());
            }
        }
        return new Result(ingested, 0, null);
    }

    private boolean ingestOne(CosmicOutboxRecord receipt) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (alreadyIngested(connection, receipt)) {
                    connection.rollback();
                    return false;
                }
                CosmicOutboxEventTranslator translator = new CosmicOutboxEventTranslator(
                        (run, character, agent) -> participant(connection, run, character, agent),
                        (run, account, item, fingerprint, quantity) -> lots(
                                connection, run, account, item, fingerprint, quantity),
                        (run, activity) -> activity(connection, run, activity));
                CosmicOutboxEventTranslator.IngestionPlan plan = translator.translate(receipt);
                insertEvent(connection, plan.event());
                insertPostings(connection, plan.event());
                insertLots(connection, plan);
                updateInstances(connection, plan.event());
                insertTransaction(connection, receipt, plan.event());
                connection.commit();
                return true;
            } catch (SQLException | JsonProcessingException failure) {
                connection.rollback();
                throw new EconomyPersistenceException("Could not ingest Cosmic receipt", failure);
            } catch (RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not ingest Cosmic receipt", failure);
        }
    }

    private List<CosmicOutboxRecord> pending(int limit) {
        String sql = "SELECT r.* FROM cosmic_outbox_receipt r WHERE r.run_id IS NOT NULL "
                + "AND NOT EXISTS (SELECT 1 FROM economic_event e WHERE e.run_id = r.run_id "
                + "AND e.idempotency_key = 'cosmic:' || r.outbox_id::text) "
                + "AND NOT EXISTS (SELECT 1 FROM economic_ingestion_failure f WHERE f.outbox_id = r.outbox_id) "
                + "ORDER BY r.logical_at, r.cosmic_created_at, r.outbox_id LIMIT ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            List<CosmicOutboxRecord> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(receipt(rows));
            }
            return List.copyOf(result);
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not read pending Cosmic receipts", failure);
        }
    }

    private static CosmicOutboxRecord receipt(ResultSet row) throws SQLException {
        Number secondary = (Number) row.getObject("secondary_character_id");
        Timestamp logicalAt = row.getTimestamp("logical_at");
        return new CosmicOutboxRecord(row.getObject("outbox_id", UUID.class),
                row.getString("idempotency_key"), row.getString("operation_kind"),
                row.getInt("primary_character_id"), secondary == null ? null : secondary.intValue(),
                row.getString("summary"), row.getString("payload"), row.getObject("run_id", UUID.class),
                logicalAt == null ? null : logicalAt.toInstant(), row.getString("decision_id"),
                row.getString("activity_id"), row.getString("config_revision"),
                row.getString("catalog_revision"), row.getString("reason_code"),
                row.getBoolean("primary_is_agent"), row.getBoolean("secondary_is_agent"),
                row.getTimestamp("cosmic_created_at").toInstant());
    }

    private static boolean alreadyIngested(Connection connection, CosmicOutboxRecord receipt)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM economic_event WHERE run_id = ? AND idempotency_key = ?")) {
            statement.setObject(1, receipt.runId());
            statement.setString(2, "cosmic:" + receipt.outboxId());
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    private static CosmicOutboxEventTranslator.Participant participant(
            Connection connection, UUID runId, int characterId, boolean markedAgent) {
        if (!markedAgent) {
            String id = "character:" + characterId;
            return new CosmicOutboxEventTranslator.Participant(id, new LedgerAccount("HUMAN", id));
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT agent_id FROM agent_character_binding WHERE run_id = ? AND character_id = ?")) {
            statement.setObject(1, runId); statement.setInt(2, characterId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new CosmicOutboxEventTranslator.EvidenceMismatchException(
                        "agent character has no run binding: " + characterId);
                String id = rows.getString(1);
                return new CosmicOutboxEventTranslator.Participant(id, LedgerAccount.agent(id));
            }
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not resolve economy participant", failure);
        }
    }

    private static List<CosmicOutboxEventTranslator.LotSlice> lots(
            Connection connection, UUID runId, LedgerAccount account, int itemId,
            String fingerprint, long requested) {
        String sql = "SELECT p.lot_id, SUM(p.quantity) AS available, MIN(e.logical_time) AS acquired_at "
                + "FROM ledger_posting p JOIN economic_event e ON e.event_id = p.event_id "
                + "JOIN item_lot l ON l.run_id = e.run_id AND l.lot_id = p.lot_id "
                + "WHERE e.run_id = ? AND p.account_type = ? AND p.account_owner_id = ? "
                + "AND p.asset_type = 'ITEM' AND p.asset_identifier = ? AND p.lot_id IS NOT NULL "
                + "AND (? = '' OR l.fingerprint = ? OR l.fingerprint IS NULL) "
                + "GROUP BY p.lot_id, l.fingerprint HAVING SUM(p.quantity) > 0 "
                + "ORDER BY CASE WHEN l.fingerprint = ? THEN 0 ELSE 1 END, acquired_at, p.lot_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, runId); statement.setString(2, account.type());
            statement.setString(3, account.ownerId()); statement.setString(4, Integer.toString(itemId));
            statement.setString(5, fingerprint); statement.setString(6, fingerprint);
            statement.setString(7, fingerprint);
            List<CosmicOutboxEventTranslator.LotSlice> result = new ArrayList<>();
            long remaining = requested;
            try (ResultSet rows = statement.executeQuery()) {
                while (remaining > 0 && rows.next()) {
                    long quantity = Math.min(remaining, rows.getLong("available"));
                    result.add(new CosmicOutboxEventTranslator.LotSlice(rows.getString("lot_id"), quantity));
                    remaining -= quantity;
                }
            }
            return List.copyOf(result);
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not allocate FIFO item lots", failure);
        }
    }

    private static CosmicOutboxEventTranslator.FarmEvidence activity(
            Connection connection, UUID runId, String activityId) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT outcome FROM activity_session WHERE run_id = ? AND activity_id = ? "
                        + "AND status = 'COMPLETED'")) {
            statement.setObject(1, runId); statement.setString(2, activityId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new CosmicOutboxEventTranslator.EvidenceMismatchException(
                        "completed activity evidence is missing: " + activityId);
                JsonNode value = JSON.readTree(rows.getString(1));
                List<CosmicOutboxEventTranslator.FarmDrop> drops = new ArrayList<>();
                for (JsonNode drop : value.path("itemDrops")) {
                    Map<String, Object> stats = JSON.convertValue(drop.path("equipmentStats"), Map.class);
                    drops.add(new CosmicOutboxEventTranslator.FarmDrop(drop.path("lotId").asText(),
                            drop.path("monsterId").asInt(), drop.path("itemId").asInt(),
                            drop.path("quantity").asInt(), stats));
                }
                List<CosmicOutboxEventTranslator.FarmConsumption> consumed = new ArrayList<>();
                for (JsonNode use : value.path("consumedItems"))
                    consumed.add(new CosmicOutboxEventTranslator.FarmConsumption(
                            use.path("itemId").asInt(), use.path("quantity").asInt()));
                return new CosmicOutboxEventTranslator.FarmEvidence(value.path("experience").asLong(),
                        value.path("mesos").asLong(), drops, consumed);
            }
        } catch (SQLException | JsonProcessingException failure) {
            throw new EconomyPersistenceException("Could not resolve completed farm evidence", failure);
        }
    }

    private static void insertEvent(Connection connection, EconomicEvent event)
            throws SQLException, JsonProcessingException {
        String sql = "INSERT INTO economic_event (event_id, run_id, logical_time, event_kind, "
                + "idempotency_key, causation_id, correlation_id, config_hash, catalog_version, actor_ids, evidence) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))";
        try (PreparedStatement s = connection.prepareStatement(sql)) {
            s.setObject(1, event.eventId()); s.setObject(2, event.runId());
            s.setTimestamp(3, Timestamp.from(event.logicalTime())); s.setString(4, event.kind().name());
            s.setString(5, event.idempotencyKey()); s.setString(6, nullable(event.causationId()));
            s.setString(7, nullable(event.correlationId())); s.setString(8, event.configHash());
            s.setString(9, event.catalogVersion()); s.setString(10, JSON.writeValueAsString(event.actorIds()));
            s.setString(11, JSON.writeValueAsString(event.evidence())); s.executeUpdate();
        }
    }

    private static void insertPostings(Connection connection, EconomicEvent event) throws SQLException {
        String sql = "INSERT INTO ledger_posting (event_id, posting_index, account_type, account_owner_id, "
                + "asset_type, asset_identifier, quantity, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement s = connection.prepareStatement(sql)) {
            int index = 0;
            for (LedgerPosting p : event.postings()) {
                s.setObject(1, event.eventId()); s.setInt(2, index++); s.setString(3, p.account().type());
                s.setString(4, p.account().ownerId()); s.setString(5, p.asset().type().name());
                s.setString(6, p.asset().identifier()); s.setLong(7, p.quantity());
                s.setString(8, nullable(p.lotId())); s.addBatch();
            }
            s.executeBatch();
        }
    }

    private static void insertLots(Connection connection, CosmicOutboxEventTranslator.IngestionPlan plan)
            throws SQLException, JsonProcessingException {
        String lotSql = "INSERT INTO item_lot (run_id, lot_id, item_id, created_event_id, source_kind, "
                + "source_identifier, original_quantity, attributes, fingerprint) VALUES (?, ?, ?, ?, ?, ?, ?, "
                + "CAST(? AS jsonb), ?)";
        String instanceSql = "INSERT INTO item_instance (run_id, instance_id, lot_id, item_id, equipment_stats, "
                + "current_owner_id, current_location) VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?)";
        try (PreparedStatement lot = connection.prepareStatement(lotSql);
             PreparedStatement instance = connection.prepareStatement(instanceSql)) {
            for (var value : plan.createdLots()) {
                lot.setObject(1, plan.event().runId()); lot.setString(2, value.lotId());
                lot.setInt(3, value.itemId()); lot.setObject(4, plan.event().eventId());
                lot.setString(5, value.sourceKind()); lot.setString(6, value.sourceIdentifier());
                lot.setLong(7, value.quantity()); lot.setString(8, JSON.writeValueAsString(value.attributes()));
                lot.setString(9, nullable(Objects.toString(value.attributes().get("fingerprint"), "")));
                lot.addBatch();
                for (var item : value.instances()) {
                    instance.setObject(1, plan.event().runId()); instance.setString(2, item.instanceId());
                    instance.setString(3, item.lotId()); instance.setInt(4, item.itemId());
                    instance.setString(5, JSON.writeValueAsString(item.stats()));
                    instance.setString(6, item.ownerId()); instance.setString(7, item.location());
                    instance.addBatch();
                }
            }
            lot.executeBatch(); instance.executeBatch();
        }
    }

    private static void updateInstances(Connection connection, EconomicEvent event) throws SQLException {
        String sql = "UPDATE item_instance SET current_owner_id = ?, current_location = ? "
                + "WHERE run_id = ? AND lot_id = ?";
        try (PreparedStatement s = connection.prepareStatement(sql)) {
            for (LedgerPosting p : event.postings()) {
                if (p.quantity() <= 0 || p.asset().type() != AssetType.ITEM || p.lotId().isBlank()) continue;
                s.setString(1, p.account().ownerId()); s.setString(2, p.account().type());
                s.setObject(3, event.runId()); s.setString(4, p.lotId()); s.addBatch();
            }
            s.executeBatch();
        }
    }

    private static void insertTransaction(Connection connection, CosmicOutboxRecord receipt,
                                          EconomicEvent event) throws SQLException, JsonProcessingException {
        String summary = receipt.summary() == null ? "" : receipt.summary();
        Map<String, String> facts = new LinkedHashMap<>();
        for (String token : summary.split("\\s+")) {
            int at = token.indexOf('='); if (at > 0) facts.put(token.substring(0, at), token.substring(at + 1));
        }
        Integer itemId = integer(facts.get("item")); Integer quantity = integer(facts.get("quantity"));
        Long gross = longValue(facts.getOrDefault("gross", facts.get("mesos")));
        long tax = Optional.ofNullable(longValue(facts.get("buyerTax"))).orElse(0L)
                + Optional.ofNullable(longValue(facts.get("sellerTax"))).orElse(0L);
        String buyer = null, seller = null;
        if (receipt.operationKind().equals("PLAYER_SHOP_SALE")) {
            buyer = event.actorIds().get(0); seller = event.actorIds().get(1);
        } else if (receipt.operationKind().equals("SHOP_BUY") || receipt.operationKind().equals("SHOP_RECHARGE")) {
            buyer = event.actorIds().getFirst();
        } else if (receipt.operationKind().equals("SHOP_SELL")) seller = event.actorIds().getFirst();
        String sql = "INSERT INTO economic_transaction (run_id, transaction_id, committed_event_id, "
                + "transaction_kind, buyer_id, seller_id, item_id, quantity, gross_mesos, tax_mesos, "
                + "human_counterparty, logical_at, evidence) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))";
        try (PreparedStatement s = connection.prepareStatement(sql)) {
            s.setObject(1, event.runId()); s.setString(2, receipt.outboxId().toString());
            s.setObject(3, event.eventId()); s.setString(4, receipt.operationKind());
            s.setString(5, buyer); s.setString(6, seller); setInteger(s, 7, itemId); setInteger(s, 8, quantity);
            setLong(s, 9, gross); s.setLong(10, tax);
            s.setBoolean(11, !receipt.primaryIsAgent() || (receipt.secondaryCharacterId() != null && !receipt.secondaryIsAgent()));
            s.setTimestamp(12, Timestamp.from(event.logicalTime()));
            s.setString(13, JSON.writeValueAsString(event.evidence())); s.executeUpdate();
        }
    }

    private void quarantine(CosmicOutboxRecord receipt, RuntimeException failure) {
        String sql = "INSERT INTO economic_ingestion_failure (outbox_id, run_id, error_class, error_message, receipt) "
                + "VALUES (?, ?, ?, ?, CAST(? AS jsonb)) ON CONFLICT (outbox_id) DO UPDATE SET "
                + "failed_at = now(), attempts = economic_ingestion_failure.attempts + 1, "
                + "error_class = EXCLUDED.error_class, error_message = EXCLUDED.error_message, receipt = EXCLUDED.receipt";
        try (Connection connection = dataSource.getConnection(); PreparedStatement s = connection.prepareStatement(sql)) {
            s.setObject(1, receipt.outboxId()); s.setObject(2, receipt.runId());
            s.setString(3, failure.getClass().getName()); s.setString(4, truncate(failure.getMessage()));
            s.setString(5, JSON.writeValueAsString(receipt)); s.executeUpdate();
        } catch (SQLException | JsonProcessingException persistenceFailure) {
            failure.addSuppressed(persistenceFailure);
            throw failure;
        }
    }

    private static Integer integer(String value) { return value == null ? null : Integer.valueOf(value); }
    private static Long longValue(String value) { return value == null ? null : Long.valueOf(value); }
    private static void setInteger(PreparedStatement s, int index, Integer value) throws SQLException {
        if (value == null) s.setNull(index, Types.INTEGER); else s.setInt(index, value);
    }
    private static void setLong(PreparedStatement s, int index, Long value) throws SQLException {
        if (value == null) s.setNull(index, Types.BIGINT); else s.setLong(index, value);
    }
    private static String nullable(String value) { return value == null || value.isBlank() ? null : value; }
    private static String truncate(String value) {
        String text = value == null ? "unknown ingestion failure" : value;
        return text.substring(0, Math.min(2048, text.length()));
    }

    public record Result(int ingested, int quarantined, UUID failedOutboxId) { }
}
