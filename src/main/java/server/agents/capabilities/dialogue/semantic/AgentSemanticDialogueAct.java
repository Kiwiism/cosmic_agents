package server.agents.capabilities.dialogue.semantic;

import server.agents.capabilities.dialogue.AgentDialogueAudience;

import java.util.Map;

/** Model output before deterministic text realization and observer projection. */
public record AgentSemanticDialogueAct(
        int speakerAgentId,
        int listenerAgentId,
        long occurredAtMs,
        String topicId,
        String actKey,
        AgentDialogueAudience audience,
        String dedupeKey,
        long cooldownMs,
        long variationSeed,
        Map<String, String> parameters) {
    public AgentSemanticDialogueAct {
        if (speakerAgentId <= 0 || listenerAgentId < 0 || occurredAtMs < 0
                || topicId == null || topicId.isBlank()
                || actKey == null || actKey.isBlank() || audience == null || cooldownMs < 0) {
            throw new IllegalArgumentException("Valid semantic dialogue identity and content are required");
        }
        topicId = topicId.trim().toLowerCase();
        actKey = actKey.trim().toLowerCase();
        dedupeKey = dedupeKey == null || dedupeKey.isBlank()
                ? topicId + ':' + actKey : dedupeKey.trim();
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
