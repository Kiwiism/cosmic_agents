package server.agents.economy.persistence;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class JdbcEconomyParticipantBindingStore implements EconomyParticipantBindingStore {
    private final DataSource dataSource;

    public JdbcEconomyParticipantBindingStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public void bind(UUID runId, String agentId, int characterId, Instant logicalAt) {
        String sql = "INSERT INTO agent_character_binding (run_id, agent_id, character_id, bound_at) "
                + "VALUES (?, ?, ?, ?) ON CONFLICT (run_id, agent_id) DO UPDATE SET "
                + "character_id = EXCLUDED.character_id, bound_at = EXCLUDED.bound_at "
                + "WHERE agent_character_binding.character_id = EXCLUDED.character_id";
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, runId); statement.setString(2, agentId);
            statement.setInt(3, characterId); statement.setTimestamp(4, Timestamp.from(logicalAt));
            if (statement.executeUpdate() != 1)
                throw new SQLException("agent was already bound to a different character");
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not bind economy agent to Cosmic character", failure);
        }
    }
}
