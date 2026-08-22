package server.agents.integration.cosmic;

import server.agents.integration.AgentIdentityGateway;
import server.agents.integration.AgentIdentityOrigin;
import server.agents.integration.AgentIdentityRecord;
import server.agents.integration.AgentIdentityStatus;
import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public enum CosmicAgentIdentityGateway implements AgentIdentityGateway {
    INSTANCE;

    @Override
    public Optional<AgentIdentityRecord> find(int characterId) throws SQLException {
        if (characterId <= 0) {
            return Optional.empty();
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT status, origin, interactive_allowed "
                             + "FROM agent_characters WHERE character_id = ?")) {
            statement.setInt(1, characterId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new AgentIdentityRecord(
                        characterId,
                        AgentIdentityStatus.valueOf(result.getString("status")),
                        AgentIdentityOrigin.valueOf(result.getString("origin")),
                        result.getBoolean("interactive_allowed")));
            }
        } catch (IllegalArgumentException invalidStoredValue) {
            throw new SQLException("Invalid Agent identity value for character " + characterId,
                    invalidStoredValue);
        }
    }

    @Override
    public void register(int characterId,
                         AgentIdentityOrigin origin,
                         boolean interactiveAllowed) throws SQLException {
        if (characterId <= 0 || origin == null) {
            throw new IllegalArgumentException("A positive character id and origin are required");
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO agent_characters "
                             + "(character_id, status, origin, interactive_allowed) VALUES (?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE character_id = VALUES(character_id)")) {
            statement.setInt(1, characterId);
            statement.setString(2, AgentIdentityStatus.ACTIVE.name());
            statement.setString(3, origin.name());
            statement.setBoolean(4, interactiveAllowed);
            statement.executeUpdate();
        }
    }
}
