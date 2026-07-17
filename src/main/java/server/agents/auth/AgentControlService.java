package server.agents.auth;

import client.Character;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentPersistenceGatewayRuntime;
import server.agents.registry.AgentResolvedCharacter;

import java.sql.SQLException;

/** Owner-free Agent character resolution and administrative authorization. */
public final class AgentControlService implements AgentControlAccess {
    private static final AgentControlService INSTANCE = new AgentControlService();

    public static AgentControlService getInstance() {
        return INSTANCE;
    }

    private AgentControlService() {
    }

    @Override
    public AgentResolvedCharacter resolveCharacterByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Character online = AgentCharacterGatewayRuntime.characters().findOnlineCharacterByName(name);
        if (online != null) {
            return resolved(online);
        }
        try {
            return AgentPersistenceGatewayRuntime.persistence().findCharacterByName(name);
        } catch (SQLException ignored) {
            return null;
        }
    }

    @Override
    public AgentResolvedCharacter resolveCharacterById(int characterId) {
        Character online = AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(characterId);
        if (online != null) {
            return resolved(online);
        }
        try {
            return AgentPersistenceGatewayRuntime.persistence().findCharacterById(characterId);
        } catch (SQLException ignored) {
            return null;
        }
    }

    @Override
    public AgentAuthorizationResult ensureCanControl(Character actor, AgentResolvedCharacter agent) {
        if (actor == null || agent == null) {
            return AgentAuthorizationResult.denied("Agent could not be resolved.");
        }
        if (!AgentAuthorityService.mayOperate(actor)) {
            return AgentAuthorizationResult.denied("You are not configured as an Agent operator.");
        }
        if (actor.getId() == agent.id()) {
            return AgentAuthorizationResult.denied("You cannot spawn your current character as an Agent.");
        }
        return AgentAuthorizationResult.allowed(false);
    }

    private static AgentResolvedCharacter resolved(Character character) {
        return new AgentResolvedCharacter(
                character.getId(),
                character.getName(),
                character.getAccountID(),
                character);
    }
}
