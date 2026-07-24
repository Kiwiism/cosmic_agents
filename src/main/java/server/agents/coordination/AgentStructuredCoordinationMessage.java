package server.agents.coordination;

import java.util.Map;

/** Extensible technical payload for protocols not requiring a dedicated record yet. */
public record AgentStructuredCoordinationMessage(
        String messageType,
        int sourceAgentCharacterId,
        long cohortId,
        int mapId,
        long createdAtMillis,
        Map<String, String> fields) implements AgentCoordinationMessage {

    public AgentStructuredCoordinationMessage {
        if (messageType == null || messageType.isBlank() || sourceAgentCharacterId <= 0
                || cohortId < 0 || mapId < 0 || createdAtMillis < 0) {
            throw new IllegalArgumentException("Valid structured coordination message is required");
        }
        fields = Map.copyOf(fields == null ? Map.of() : fields);
    }
}
