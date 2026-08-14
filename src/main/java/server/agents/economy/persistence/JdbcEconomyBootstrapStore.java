package server.agents.economy.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.economy.domain.*;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Records imported Cosmic state as an explicit baseline source, never as simulated production. */
public final class JdbcEconomyBootstrapStore implements EconomyBootstrapStore {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DataSource dataSource;

    public JdbcEconomyBootstrapStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public void recordImported(UUID runId, String agentId, Instant logicalAt, String configHash,
                               String catalogVersion, EconomyBootstrapSnapshot snapshot) {
        String key = "bootstrap:imported:" + agentId;
        UUID eventId = UUID.nameUUIDFromBytes((runId + ":" + key).getBytes(StandardCharsets.UTF_8));
        List<LedgerPosting> postings = postings(agentId, snapshot);
        EconomicEvent event = new EconomicEvent(eventId, runId, logicalAt,
                EconomicEventKind.INITIAL_ENDOWMENT, key, "", key, configHash, catalogVersion,
                List.of(agentId), Map.of("source", "IMPORTED_COSMIC_STATE",
                "characterId", Integer.toString(snapshot.characterId()),
                "level", Integer.toString(snapshot.level()),
                "experience", Long.toString(snapshot.experience()),
                "holdingCount", Integer.toString(snapshot.holdings().size())), postings);
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit(); connection.setAutoCommit(false);
            try {
                if (exists(connection, runId, key)) { connection.rollback(); return; }
                insertEvent(connection, event); insertPostings(connection, event);
                insertLots(connection, event, agentId, snapshot);
                connection.commit();
            } catch (SQLException | JsonProcessingException failure) {
                connection.rollback();
                throw new EconomyPersistenceException("Could not record imported Cosmic baseline", failure);
            } finally { connection.setAutoCommit(autoCommit); }
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not record imported Cosmic baseline", failure);
        }
    }

    private static List<LedgerPosting> postings(String agentId, EconomyBootstrapSnapshot snapshot) {
        List<LedgerPosting> result = new ArrayList<>();
        LedgerAccount source = LedgerAccount.source("IMPORTED_COSMIC_STATE");
        LedgerAccount agent = LedgerAccount.agent(agentId);
        if (snapshot.mesos() > 0) transfer(result, source, agent, AssetKey.MESO, snapshot.mesos(), "");
        int index = 0;
        for (var holding : snapshot.holdings()) {
            if (holding.quantity() <= 0) continue;
            String lot = lotId(agentId, holding, index++);
            transfer(result, source, agent, AssetKey.item(holding.itemId()), holding.quantity(), lot);
        }
        return result;
    }

    private static void insertLots(Connection connection, EconomicEvent event, String agentId,
                                   EconomyBootstrapSnapshot snapshot)
            throws SQLException, JsonProcessingException {
        String lotSql = "INSERT INTO item_lot (run_id, lot_id, item_id, created_event_id, source_kind, "
                + "source_identifier, original_quantity, attributes, fingerprint) VALUES (?, ?, ?, ?, "
                + "'IMPORTED_COSMIC_STATE', ?, ?, CAST(? AS jsonb), ?)";
        String instanceSql = "INSERT INTO item_instance (run_id, instance_id, lot_id, item_id, equipment_stats, "
                + "current_owner_id, current_location) VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, 'AGENT')";
        try (PreparedStatement lot = connection.prepareStatement(lotSql);
             PreparedStatement instance = connection.prepareStatement(instanceSql)) {
            int index = 0;
            for (var holding : snapshot.holdings()) {
                if (holding.quantity() <= 0) continue;
                String lotId = lotId(agentId, holding, index++);
                lot.setObject(1, event.runId()); lot.setString(2, lotId); lot.setInt(3, holding.itemId());
                lot.setObject(4, event.eventId()); lot.setString(5, "character:" + snapshot.characterId());
                lot.setLong(6, holding.quantity()); lot.setString(7, JSON.writeValueAsString(holding.attributes()));
                lot.setString(8, holding.fingerprint()); lot.addBatch();
                if (holding.equipment()) {
                    if (holding.quantity() != 1)
                        throw new IllegalStateException("Imported equipment quantity is not one");
                    instance.setObject(1, event.runId()); instance.setString(2, lotId + ":instance");
                    instance.setString(3, lotId); instance.setInt(4, holding.itemId());
                    instance.setString(5, JSON.writeValueAsString(holding.attributes()));
                    instance.setString(6, agentId); instance.addBatch();
                }
            }
            lot.executeBatch(); instance.executeBatch();
        }
    }

    private static boolean exists(Connection connection, UUID runId, String key) throws SQLException {
        try (PreparedStatement s = connection.prepareStatement(
                "SELECT 1 FROM economic_event WHERE run_id = ? AND idempotency_key = ?")) {
            s.setObject(1, runId); s.setString(2, key);
            try (ResultSet rows = s.executeQuery()) { return rows.next(); }
        }
    }

    private static void insertEvent(Connection connection, EconomicEvent event)
            throws SQLException, JsonProcessingException {
        try (PreparedStatement s = connection.prepareStatement(
                "INSERT INTO economic_event (event_id, run_id, logical_time, event_kind, idempotency_key, "
                        + "causation_id, correlation_id, config_hash, catalog_version, actor_ids, evidence) "
                        + "VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))")) {
            s.setObject(1, event.eventId()); s.setObject(2, event.runId());
            s.setTimestamp(3, Timestamp.from(event.logicalTime())); s.setString(4, event.kind().name());
            s.setString(5, event.idempotencyKey()); s.setString(6, event.correlationId());
            s.setString(7, event.configHash()); s.setString(8, event.catalogVersion());
            s.setString(9, JSON.writeValueAsString(event.actorIds()));
            s.setString(10, JSON.writeValueAsString(event.evidence())); s.executeUpdate();
        }
    }

    private static void insertPostings(Connection connection, EconomicEvent event) throws SQLException {
        try (PreparedStatement s = connection.prepareStatement(
                "INSERT INTO ledger_posting (event_id, posting_index, account_type, account_owner_id, "
                        + "asset_type, asset_identifier, quantity, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            int index = 0;
            for (LedgerPosting p : event.postings()) {
                s.setObject(1, event.eventId()); s.setInt(2, index++); s.setString(3, p.account().type());
                s.setString(4, p.account().ownerId()); s.setString(5, p.asset().type().name());
                s.setString(6, p.asset().identifier()); s.setLong(7, p.quantity());
                s.setString(8, p.lotId().isBlank() ? null : p.lotId()); s.addBatch();
            }
            s.executeBatch();
        }
    }

    private static String lotId(String agentId, EconomyBootstrapSnapshot.Holding holding, int index) {
        return "import:" + agentId + ':' + holding.itemId() + ':' + holding.fingerprint() + ':' + index;
    }
    private static void transfer(List<LedgerPosting> result, LedgerAccount from, LedgerAccount to,
                                 AssetKey asset, long quantity, String lot) {
        result.add(new LedgerPosting(from, asset, -quantity, lot));
        result.add(new LedgerPosting(to, asset, quantity, lot));
    }
}
