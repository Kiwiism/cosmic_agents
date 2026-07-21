package server.agents.capabilities.dialogue.conversation;

public record AgentConversationSessionView(
        long conversationId,
        int firstAgentId,
        int secondAgentId,
        int mapId,
        String topicId,
        int completedTurns,
        int maxTurns,
        long startedAtMs,
        long expiresAtMs) {
}
