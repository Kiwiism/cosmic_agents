package server.agents.field.events;

import server.agents.events.AgentContextualEvent;

/** Correlated lifecycle fact for one Agent's managed field visit. */
public record AgentFieldLifecycleEvent(
        int agentId,
        long occurredAtMs,
        int mapId,
        String sessionId,
        String requestId,
        String callerId,
        Phase phase,
        String reason,
        String objectiveId) implements AgentContextualEvent {
    public static final String TYPE = "field.lifecycle";

    public AgentFieldLifecycleEvent {
        sessionId = normalize(sessionId);
        requestId = normalize(requestId);
        callerId = normalize(callerId);
        reason = normalize(reason);
        objectiveId = normalize(objectiveId);
        if (agentId <= 0 || occurredAtMs < 0L || mapId <= 0 || sessionId.isEmpty()
                || requestId.isEmpty() || callerId.isEmpty() || phase == null) {
            throw new IllegalArgumentException("valid field lifecycle event fields are required");
        }
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String dedupeKey() {
        return sessionId + ':' + phase;
    }

    public enum Phase {
        REQUESTED,
        ADMITTED,
        FORMING,
        GRINDING,
        RESTING,
        SUSPENDED,
        RESUMED,
        DRAINING,
        EXITED,
        FAILED
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
