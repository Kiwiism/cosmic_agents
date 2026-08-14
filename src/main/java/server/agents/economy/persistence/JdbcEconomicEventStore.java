package server.agents.economy.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.economy.domain.*;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** PostgreSQL append-only store. It is never used to mutate Cosmic character state. */
public final class JdbcEconomicEventStore implements EconomicEventStore {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DataSource dataSource;

    public JdbcEconomicEventStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public boolean append(EconomicEvent event) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertEvent(connection, event);
                insertPostings(connection, event);
                connection.commit();
                return true;
            } catch (SQLException failure) {
                connection.rollback();
                if ("23505".equals(failure.getSQLState())) return false;
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException | JsonProcessingException failure) {
            throw new EconomyPersistenceException("Could not append economic event", failure);
        }
    }

    @Override
    public List<EconomicEvent> read(UUID runId, Instant afterExclusive, int limit) {
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        String sql = "SELECT * FROM economic_event WHERE run_id = ? AND logical_time > ? "
                + "ORDER BY logical_time, event_id LIMIT ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, runId);
            statement.setTimestamp(2, Timestamp.from(afterExclusive));
            statement.setInt(3, limit);
            List<EconomicEvent> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(readEvent(connection, rows));
            }
            return List.copyOf(result);
        } catch (SQLException | JsonProcessingException failure) {
            throw new EconomyPersistenceException("Could not read economic events", failure);
        }
    }

    private static void insertEvent(Connection connection, EconomicEvent event)
            throws SQLException, JsonProcessingException {
        String sql = "INSERT INTO economic_event (event_id, run_id, logical_time, event_kind, "
                + "idempotency_key, causation_id, correlation_id, config_hash, catalog_version, "
                + "actor_ids, evidence) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, event.eventId());
            statement.setObject(2, event.runId());
            statement.setTimestamp(3, Timestamp.from(event.logicalTime()));
            statement.setString(4, event.kind().name());
            statement.setString(5, event.idempotencyKey());
            statement.setString(6, blankToNull(event.causationId()));
            statement.setString(7, blankToNull(event.correlationId()));
            statement.setString(8, event.configHash());
            statement.setString(9, event.catalogVersion());
            statement.setString(10, JSON.writeValueAsString(event.actorIds()));
            statement.setString(11, JSON.writeValueAsString(event.evidence()));
            statement.executeUpdate();
        }
    }

    private static void insertPostings(Connection connection, EconomicEvent event) throws SQLException {
        String sql = "INSERT INTO ledger_posting (event_id, posting_index, account_type, "
                + "account_owner_id, asset_type, asset_identifier, quantity, lot_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 0;
            for (LedgerPosting posting : event.postings()) {
                statement.setObject(1, event.eventId());
                statement.setInt(2, index++);
                statement.setString(3, posting.account().type());
                statement.setString(4, posting.account().ownerId());
                statement.setString(5, posting.asset().type().name());
                statement.setString(6, posting.asset().identifier());
                statement.setLong(7, posting.quantity());
                statement.setString(8, blankToNull(posting.lotId()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @SuppressWarnings("unchecked")
    private static EconomicEvent readEvent(Connection connection, ResultSet row)
            throws SQLException, JsonProcessingException {
        UUID eventId = row.getObject("event_id", UUID.class);
        List<LedgerPosting> postings = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM ledger_posting WHERE event_id = ? ORDER BY posting_index")) {
            statement.setObject(1, eventId);
            try (ResultSet postingRows = statement.executeQuery()) {
                while (postingRows.next()) {
                    postings.add(new LedgerPosting(
                            new LedgerAccount(postingRows.getString("account_type"),
                                    postingRows.getString("account_owner_id")),
                            new AssetKey(AssetType.valueOf(postingRows.getString("asset_type")),
                                    postingRows.getString("asset_identifier")),
                            postingRows.getLong("quantity"), postingRows.getString("lot_id")));
                }
            }
        }
        List<String> actors = JSON.readValue(row.getString("actor_ids"), List.class);
        Map<String, Object> evidence = JSON.readValue(row.getString("evidence"), Map.class);
        return new EconomicEvent(eventId, row.getObject("run_id", UUID.class),
                row.getTimestamp("logical_time").toInstant(),
                EconomicEventKind.valueOf(row.getString("event_kind")),
                row.getString("idempotency_key"), row.getString("causation_id"),
                row.getString("correlation_id"), row.getString("config_hash"),
                row.getString("catalog_version"), actors, evidence, postings);
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
}
