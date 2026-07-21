package server.agents.events.journal;

import server.agents.events.AgentEventContext;

import java.util.Map;

/** Stable JSON-lines representation of one selected Agent event. */
public record AgentEventJournalRecord(
        String eventId,
        int agentId,
        long occurredAtMs,
        String type,
        AgentEventContext context,
        Map<String, Object> payload) {

    public AgentEventJournalRecord {
        if (eventId == null || eventId.isBlank() || agentId <= 0 || occurredAtMs < 0
                || type == null || type.isBlank() || context == null) {
            throw new IllegalArgumentException("Valid durable event identity and context are required");
        }
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
