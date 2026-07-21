package server.agents.coordination;

import java.util.Map;

/** Direct semantic turn delivered privately even when public presentation is suppressed. */
public record AgentConversationCoordinationMessage(
        int sourceAgentCharacterId,
        int targetAgentCharacterId,
        long cohortId,
        int mapId,
        long createdAtMillis,
        long conversationId,
        int turnIndex,
        String topicId,
        String actKey,
        Map<String, String> parameters) implements AgentCoordinationMessage {
    public AgentConversationCoordinationMessage {
        if (sourceAgentCharacterId <= 0 || targetAgentCharacterId <= 0
                || mapId < 0 || createdAtMillis < 0 || conversationId <= 0
                || turnIndex < 0 || topicId == null || topicId.isBlank()
                || actKey == null || actKey.isBlank()) {
            throw new IllegalArgumentException("Valid direct conversation message is required");
        }
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
