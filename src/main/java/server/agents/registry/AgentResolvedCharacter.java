package server.agents.registry;

import client.BotClient;
import client.Character;

public record AgentResolvedCharacter(int id, String name, int accountId, Character onlineCharacter) {
    public boolean isOnline() {
        return onlineCharacter != null;
    }

    public boolean isOnlineAsBot() {
        return onlineCharacter != null && onlineCharacter.getClient() instanceof BotClient;
    }
}
