package server.agents.integration.cosmic;

import client.DefaultDates;
import server.agents.integration.AgentAccountResolution;
import server.agents.integration.AgentPersistenceGateway;
import server.agents.registry.AgentResolvedCharacter;
import tools.BCrypt;
import tools.DatabaseConnection;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Base64;

public final class CosmicAgentPersistenceGateway implements AgentPersistenceGateway {
    public static final CosmicAgentPersistenceGateway INSTANCE = new CosmicAgentPersistenceGateway();
    private static final SecureRandom PASSWORD_RANDOM = new SecureRandom();

    private CosmicAgentPersistenceGateway() {
    }

    @Override
    public AgentResolvedCharacter findCharacterByName(String name) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT id, name, accountid FROM characters WHERE LOWER(name) = LOWER(?)")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new AgentResolvedCharacter(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("accountid"),
                        null);
            }
        }
    }

    @Override
    public AgentResolvedCharacter findCharacterById(int characterId) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT name, accountid FROM characters WHERE id = ?")) {
            ps.setInt(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new AgentResolvedCharacter(
                        characterId,
                        rs.getString("name"),
                        rs.getInt("accountid"),
                        null);
            }
        }
    }

    @Override
    public AgentAccountResolution resolveOrCreateAgentAccount(String name) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection()) {
            Integer existingAccountId = findAccountId(con, name);
            if (existingAccountId != null) {
                return AgentAccountResolution.failure(
                        "Account '" + name + "' already exists, so it cannot be used for Agent creation.");
            }

            int createdAccountId = createAgentAccount(con, name);
            return createdAccountId > 0
                    ? AgentAccountResolution.created(createdAccountId)
                    : AgentAccountResolution.failure("Failed to create or reuse the bot account for '" + name + "'.");
        }
    }

    private Integer findAccountId(Connection con, String name) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id FROM accounts WHERE LOWER(name) = LOWER(?)")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("id") : null;
            }
        }
    }

    private int createAgentAccount(Connection con, String name) throws SQLException {
        String hashedPw = randomPasswordHash();
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO accounts (name, password, birthday, tempban) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, hashedPw);
            ps.setDate(3, Date.valueOf(DefaultDates.getBirthday()));
            ps.setTimestamp(4, Timestamp.valueOf(DefaultDates.getTempban()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private String randomPasswordHash() {
        byte[] randomPassword = new byte[32];
        PASSWORD_RANDOM.nextBytes(randomPassword);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(randomPassword);
        return BCrypt.hashpw(encoded, BCrypt.gensalt(12));
    }
}
