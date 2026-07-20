package server.agents.events.journal;

import java.util.Set;

/** Bounded filters for explicit operator, test, or offline durable-event replay. */
public record AgentEventReplayQuery(
        Integer agentId,
        String objectiveId,
        String correlationId,
        Set<String> eventTypes,
        long fromMs,
        long toMs,
        int limit) {
    public static final int MAX_LIMIT = 1000;

    public AgentEventReplayQuery {
        if (agentId != null && agentId <= 0 || fromMs < 0 || toMs < fromMs
                || limit <= 0 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Valid replay filters, time range, and bounded limit are required");
        }
        objectiveId = normalize(objectiveId);
        correlationId = normalize(correlationId);
        eventTypes = eventTypes == null ? Set.of() : Set.copyOf(eventTypes);
        if (eventTypes.stream().anyMatch(type -> type == null || type.isBlank())) {
            throw new IllegalArgumentException("Replay event types must be non-blank");
        }
    }

    public static AgentEventReplayQuery all(int limit) {
        return new AgentEventReplayQuery(null, "", "", Set.of(), 0L, Long.MAX_VALUE, limit);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
