package server.agents.auth;

import client.Character;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentPersistenceGatewayRuntime;
import server.agents.registry.AgentResolvedCharacter;
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

        try {
            return AgentPersistenceGatewayRuntime.persistence().findCharacterByName(name);
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
        try {
            return AgentPersistenceGatewayRuntime.persistence().getRegisteredOwnerId(botCharId);
        } catch (SQLException e) {
            return null;
        }
    }

    public void registerOwner(int botCharId, int ownerCharId) {
        try {
            AgentPersistenceGatewayRuntime.persistence().registerOwner(botCharId, ownerCharId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register bot owner", e);
        }
    }

    public AgentResolvedCharacter resolveCharacterById(int charId) {
        Character online = AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(charId);
        if (online != null) {
            return new AgentResolvedCharacter(charId, online.getName(), online.getAccountID(), online);
        }
        try {
            return AgentPersistenceGatewayRuntime.persistence().findCharacterById(charId);
        } catch (SQLException e) {
            return null;
        }
    }

    private Character findOnlineCharacter(String name) {
        return AgentCharacterGatewayRuntime.characters().findOnlineCharacterByName(name);
    }

}
