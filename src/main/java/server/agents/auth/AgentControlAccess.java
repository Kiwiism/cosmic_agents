package server.agents.auth;

import client.Character;
import server.agents.registry.AgentResolvedCharacter;

/** Character resolution and administrative control authorization at command ingress. */
public interface AgentControlAccess {
    AgentResolvedCharacter resolveCharacterByName(String name);

    AgentResolvedCharacter resolveCharacterById(int characterId);

    AgentAuthorizationResult ensureCanControl(Character actor, AgentResolvedCharacter agent);
}
