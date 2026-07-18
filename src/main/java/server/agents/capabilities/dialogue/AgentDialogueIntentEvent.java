package server.agents.capabilities.dialogue;

import server.agents.events.AgentEvent;

import java.util.Map;

/** Presentation request. Operational Agent-to-Agent coordination does not use this event. */
public record AgentDialogueIntentEvent(
        int agentId,
        long occurredAtMs,
        String intentKey,
        AgentDialogueAudience audience,
        String dedupeKey,
        long cooldownMs,
        Map<String, String> parameters) implements AgentEvent {

    public AgentDialogueIntentEvent {
        if (agentId <= 0 || occurredAtMs < 0 || intentKey == null || intentKey.isBlank()
                || audience == null || cooldownMs < 0) {
            throw new IllegalArgumentException("Valid dialogue identity, audience, and cooldown are required");
        }
        dedupeKey = dedupeKey == null || dedupeKey.isBlank() ? intentKey : dedupeKey;
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    @Override
    public String type() {
        return "dialogue.intent";
    }
}
