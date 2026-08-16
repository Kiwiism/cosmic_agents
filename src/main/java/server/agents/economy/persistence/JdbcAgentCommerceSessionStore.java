package server.agents.economy.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.runtime.commerce.AgentCommerceSessionCheckpoint;
import server.agents.runtime.commerce.AgentCommerceSessionStore;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** PostgreSQL-backed per-Agent Commerce session checkpoints. */
public final class JdbcAgentCommerceSessionStore implements AgentCommerceSessionStore {
    private final DataSource database;
    private final ObjectMapper mapper = new ObjectMapper();

    public JdbcAgentCommerceSessionStore(DataSource database) {
        this.database = Objects.requireNonNull(database, "Commerce checkpoint database");
    }

    @Override
    public void save(AgentCommerceSessionCheckpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "Commerce checkpoint");
        String json;
        try {
            json = mapper.writeValueAsString(checkpoint);
        } catch (JsonProcessingException failure) {
            throw new EconomyPersistenceException(
                    "Could not encode Commerce session checkpoint", failure);
        }
        String sql = "INSERT INTO agent_commerce_session_checkpoint "
                + "(agent_id, schema_version, request_id, session_id, phase, updated_at, checkpoint) "
                + "VALUES (?, ?, ?, ?::uuid, ?, ?, ?::jsonb) "
                + "ON CONFLICT (agent_id) DO UPDATE SET schema_version=EXCLUDED.schema_version, "
                + "request_id=EXCLUDED.request_id, session_id=EXCLUDED.session_id, "
                + "phase=EXCLUDED.phase, updated_at=EXCLUDED.updated_at, "
                + "checkpoint=EXCLUDED.checkpoint";
        try (var connection = database.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, checkpoint.request().participant().agentId());
            statement.setInt(2, checkpoint.schemaVersion());
            statement.setString(3, checkpoint.request().requestId());
            statement.setString(4, checkpoint.sessionId().isEmpty()
                    ? null : checkpoint.sessionId());
            statement.setString(5, checkpoint.phase().name());
            statement.setTimestamp(6, Timestamp.from(Instant.ofEpochMilli(checkpoint.updatedAtMs())));
            statement.setString(7, json);
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new EconomyPersistenceException(
                    "Could not persist Commerce session checkpoint", failure);
        }
    }

    @Override
    public Optional<AgentCommerceSessionCheckpoint> load(String agentId) {
        String sql = "SELECT checkpoint::text FROM agent_commerce_session_checkpoint "
                + "WHERE agent_id = ?";
        try (var connection = database.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, required(agentId));
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                return Optional.of(mapper.readValue(
                        rows.getString(1), AgentCommerceSessionCheckpoint.class));
            }
        } catch (SQLException | JsonProcessingException failure) {
            throw new EconomyPersistenceException(
                    "Could not restore Commerce session checkpoint", failure);
        }
    }

    @Override
    public void delete(String agentId) {
        try (var connection = database.getConnection();
             var statement = connection.prepareStatement(
                     "DELETE FROM agent_commerce_session_checkpoint WHERE agent_id = ?")) {
            statement.setString(1, required(agentId));
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new EconomyPersistenceException(
                    "Could not delete Commerce session checkpoint", failure);
        }
    }

    private static String required(String agentId) {
        String value = agentId == null ? "" : agentId.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Commerce agent id is required");
        return value;
    }
}
