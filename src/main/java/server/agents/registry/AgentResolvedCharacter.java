package server.agents.registry;

import client.Character;
import server.agents.integration.AgentCharacterGatewayRuntime;

public record AgentResolvedCharacter(int id, String name, int accountId, Character onlineCharacter) {
    public boolean isOnline() {
        return onlineCharacter != null;
    }

    public boolean isOnlineHeadlessControlled() {
        return AgentCharacterGatewayRuntime.characters().isHeadlessControlled(onlineCharacter);
    }
}
