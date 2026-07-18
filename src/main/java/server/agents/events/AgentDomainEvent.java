package server.agents.events;

import java.util.Map;

/** Generic transport-safe event used until a family warrants a dedicated record. */
public record AgentDomainEvent(
        int agentId,
        long occurredAtMs,
        String type,
        String dedupeKey,
        Map<String, String> attributes) implements AgentEvent {

    public AgentDomainEvent {
        if (agentId <= 0 || occurredAtMs < 0 || type == null || type.isBlank()) {
            throw new IllegalArgumentException("Agent id, timestamp, and event type are required");
        }
        type = type.trim();
        dedupeKey = dedupeKey == null ? "" : dedupeKey;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
