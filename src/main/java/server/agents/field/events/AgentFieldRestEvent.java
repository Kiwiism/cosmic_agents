package server.agents.field.events;

import server.agents.events.AgentContextualEvent;

import java.awt.Point;

/** A bounded safe-spot respite that preserves field-session membership. */
public record AgentFieldRestEvent(
        int agentId,
        long occurredAtMs,
        int mapId,
        String sessionId,
        Phase phase,
        Point target,
        long plannedDurationMs,
        String reason,
        String objectiveId) implements AgentContextualEvent {
    public static final String TYPE = "field.rest";

    public AgentFieldRestEvent {
        sessionId = normalize(sessionId);
        reason = normalize(reason);
        objectiveId = normalize(objectiveId);
        target = target == null ? new Point() : new Point(target);
        if (agentId <= 0 || occurredAtMs < 0L || mapId <= 0 || sessionId.isEmpty()
                || phase == null || plannedDurationMs < 0L) {
            throw new IllegalArgumentException("valid field rest event fields are required");
        }
    }

    @Override
    public Point target() {
        return new Point(target);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String dedupeKey() {
        return sessionId + ":rest:" + phase + ':' + occurredAtMs;
    }

    public enum Phase { STARTED, ARRIVED, COMPLETED, CANCELLED }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
