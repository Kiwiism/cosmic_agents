package server.agents.economy.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.economy.communication.EconomicIntent;
import server.agents.economy.communication.EconomyCommunicationPort;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Durable structured intents that can be negotiated now and settled later at a physical venue. */
public final class JdbcEconomyCommunicationPort implements EconomyCommunicationPort {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final UUID runId;
    private final DataSource dataSource;

    public JdbcEconomyCommunicationPort(UUID runId, DataSource dataSource) {
        this.runId = Objects.requireNonNull(runId); this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public EconomicIntent publish(String actor, String counterparty, EconomicIntent.Kind kind,
                                  int itemId, String fingerprint, int quantity, long mesos,
                                  Integer mapId, String text, Map<String, Object> attributes,
                                  Instant at, Duration lifetime) {
        if (lifetime == null || lifetime.isZero() || lifetime.isNegative())
            throw new IllegalArgumentException("intent lifetime must be positive");
        EconomicIntent intent = new EconomicIntent(UUID.randomUUID(), runId, actor, counterparty,
                kind, itemId, fingerprint, quantity, mesos, mapId, text, attributes,
                at, at.plus(lifetime), EconomicIntent.Status.OPEN);
        String sql = "INSERT INTO economic_intent(intent_id,run_id,actor_agent_id,counterparty_agent_id,"
                + "kind,item_id,item_fingerprint,quantity,mesos,preferred_map_id,public_text,attributes,"
                + "created_at,expires_at,status) VALUES (?,?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?,?,?)";
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, intent.intentId()); statement.setObject(2, runId);
            statement.setString(3, actor); statement.setString(4, blankToNull(counterparty));
            statement.setString(5, kind.name()); statement.setInt(6, itemId);
            statement.setString(7, blankToNull(fingerprint)); statement.setInt(8, quantity);
            statement.setLong(9, mesos); if (mapId == null) statement.setNull(10, Types.INTEGER);
            else statement.setInt(10, mapId); statement.setString(11, text);
            statement.setString(12, JSON.writeValueAsString(intent.attributes()));
            statement.setTimestamp(13, Timestamp.from(at));
            statement.setTimestamp(14, Timestamp.from(intent.expiresAt()));
            statement.setString(15, intent.status().name()); statement.executeUpdate(); return intent;
        } catch (SQLException | JsonProcessingException failure) {
            throw new EconomyPersistenceException("Could not publish economic intent", failure);
        }
    }

    @Override
    public List<EconomicIntent> discover(String requestingAgentId, int itemId, Instant asOf, int limit) {
        if (requestingAgentId == null || requestingAgentId.isBlank() || itemId <= 0 || limit <= 0)
            throw new IllegalArgumentException("invalid economic intent discovery");
        String sql = "SELECT * FROM economic_intent WHERE run_id=? AND item_id=? AND status='OPEN' "
                + "AND created_at<=? AND expires_at>? AND actor_agent_id<>? "
                + "AND (counterparty_agent_id IS NULL OR counterparty_agent_id=?) "
                + "ORDER BY created_at,intent_id LIMIT ?";
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, runId); statement.setInt(2, itemId);
            statement.setTimestamp(3, Timestamp.from(asOf)); statement.setTimestamp(4, Timestamp.from(asOf));
            statement.setString(5, requestingAgentId); statement.setString(6, requestingAgentId);
            statement.setInt(7, limit);
            try (var rows = statement.executeQuery()) {
                List<EconomicIntent> result = new ArrayList<>();
                while (rows.next()) result.add(read(rows)); return List.copyOf(result);
            }
        } catch (SQLException | JsonProcessingException failure) {
            throw new EconomyPersistenceException("Could not discover economic intents", failure);
        }
    }

    @Override
    public boolean resolve(String requestingAgentId, UUID intentId, EconomicIntent.Status status,
                           Instant at, String reason) {
        if (requestingAgentId == null || requestingAgentId.isBlank())
            throw new IllegalArgumentException("requesting agent is required");
        if (status == EconomicIntent.Status.OPEN)
            throw new IllegalArgumentException("resolved intent cannot remain open");
        String sql = "UPDATE economic_intent SET status=?,resolved_at=?,resolution_reason=? "
                + "WHERE intent_id=? AND run_id=? AND status='OPEN' "
                + "AND (actor_agent_id=? OR counterparty_agent_id=?)";
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name()); statement.setTimestamp(2, Timestamp.from(at));
            statement.setString(3, reason); statement.setObject(4, intentId); statement.setObject(5, runId);
            statement.setString(6, requestingAgentId); statement.setString(7, requestingAgentId);
            return statement.executeUpdate() == 1;
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not resolve economic intent", failure);
        }
    }

    @SuppressWarnings("unchecked")
    private EconomicIntent read(ResultSet row) throws SQLException, JsonProcessingException {
        String counterparty = row.getString("counterparty_agent_id");
        String fingerprint = row.getString("item_fingerprint");
        int preferred = row.getInt("preferred_map_id");
        boolean preferredMissing = row.wasNull();
        return new EconomicIntent(row.getObject("intent_id", UUID.class), runId,
                row.getString("actor_agent_id"), counterparty, EconomicIntent.Kind.valueOf(row.getString("kind")),
                row.getInt("item_id"), fingerprint, row.getInt("quantity"), row.getLong("mesos"),
                preferredMissing ? null : preferred, row.getString("public_text"),
                JSON.readValue(row.getString("attributes"), Map.class),
                row.getTimestamp("created_at").toInstant(), row.getTimestamp("expires_at").toInstant(),
                EconomicIntent.Status.valueOf(row.getString("status")));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
