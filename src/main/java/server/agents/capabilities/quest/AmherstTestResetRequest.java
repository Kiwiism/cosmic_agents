package server.agents.capabilities.quest;

public record AmherstTestResetRequest(int characterId, String characterName, AmherstTestResetMode mode, int questId) {
}
