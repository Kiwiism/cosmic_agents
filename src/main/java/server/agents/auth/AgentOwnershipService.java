package server.agents.auth;

import client.Character;
import net.server.Server;
import server.agents.registry.AgentResolvedCharacter;
import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class AgentOwnershipService {
    private static final AgentOwnershipService instance = new AgentOwnershipService();

    public static AgentOwnershipService getInstance() {
        return instance;
    }

    private AgentOwnershipService() {
    }

    public AgentResolvedCharacter resolveCharacterByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        Character online = findOnlineCharacter(name);
        if (online != null) {
            return new AgentResolvedCharacter(
                    online.getId(),
                    online.getName(),
                    online.getAccountID(),
                    online);
        }

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
        } catch (SQLException e) {
            return null;
        }
    }

    public AgentAuthorizationResult ensureCanControl(Character owner, AgentResolvedCharacter bot) {
        if (owner == null || bot == null) {
            return AgentAuthorizationResult.denied("Bot could not be resolved.");
        }
        if (bot.id() == owner.getId()) {
            return AgentAuthorizationResult.denied("You cannot spawn your current character as a bot.");
        }

        Integer registeredOwnerId = getRegisteredOwnerId(bot.id());
        if (registeredOwnerId != null) {
            if (registeredOwnerId == owner.getId()) {
                return AgentAuthorizationResult.allowed(false);
            }
            if (bot.accountId() == owner.getAccountID()) {
                registerOwner(bot.id(), owner.getId());
                return AgentAuthorizationResult.allowed(true);
            }

            String registeredOwnerName = Character.getNameById(registeredOwnerId);
            String ownerName = registeredOwnerName != null ? registeredOwnerName : "another character";
            return AgentAuthorizationResult.denied(
                    "Bot '" + bot.name() + "' is registered to '" + ownerName + "'. Log in on "
                            + bot.name() + " and use @registerbot " + owner.getName() + " to change owner.");
        }

        if (bot.accountId() == owner.getAccountID()) {
            registerOwner(bot.id(), owner.getId());
            return AgentAuthorizationResult.allowed(true);
        }

        return AgentAuthorizationResult.denied(
                "Bot '" + bot.name() + "' is not registered to you. Log in on "
                        + bot.name() + " and use @registerbot " + owner.getName() + ".");
    }

    public boolean isAuthorizedOwner(int botCharId, int ownerCharId) {
        Integer registeredOwnerId = getRegisteredOwnerId(botCharId);
        return registeredOwnerId != null && registeredOwnerId == ownerCharId;
    }

    public Integer getRegisteredOwnerId(int botCharId) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT owner_char_id FROM bot_owners WHERE bot_char_id = ?")) {
            ps.setInt(1, botCharId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("owner_char_id");
                }
            }
        } catch (SQLException e) {
            return null;
        }
        return null;
    }

    public void registerOwner(int botCharId, int ownerCharId) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO bot_owners (bot_char_id, owner_char_id) VALUES (?, ?) "
                             + "ON DUPLICATE KEY UPDATE owner_char_id = VALUES(owner_char_id)")) {
            ps.setInt(1, botCharId);
            ps.setInt(2, ownerCharId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register bot owner", e);
        }
    }

    public AgentResolvedCharacter resolveCharacterById(int charId) {
        for (var world : Server.getInstance().getWorlds()) {
            Character online = world.getPlayerStorage().getCharacterById(charId);
            if (online != null) {
                return new AgentResolvedCharacter(charId, online.getName(), online.getAccountID(), online);
            }
        }
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT name, accountid FROM characters WHERE id = ?")) {
            ps.setInt(1, charId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new AgentResolvedCharacter(charId, rs.getString("name"), rs.getInt("accountid"), null);
                }
            }
        } catch (SQLException e) {
            return null;
        }
        return null;
    }

    private Character findOnlineCharacter(String name) {
        for (var world : Server.getInstance().getWorlds()) {
            Character online = world.getPlayerStorage().getCharacterByName(name);
            if (online != null) {
                return online;
            }
        }
        return null;
    }

}
