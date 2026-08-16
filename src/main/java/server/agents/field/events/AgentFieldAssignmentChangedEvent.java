package server.agents.field.events;

import server.agents.events.AgentContextualEvent;
import server.agents.field.AgentFieldRole;

import java.awt.Point;
import java.util.List;

/** Semantic territory or formation change; revision-only refreshes are not emitted. */
public record AgentFieldAssignmentChangedEvent(
        int agentId,
        long occurredAtMs,
        int mapId,
        String sessionId,
        long revision,
        AgentFieldRole role,
        int partySlot,
        List<String> cellIds,
        List<Integer> regionIds,
        Point anchor,
        String reason,
        String objectiveId) implements AgentContextualEvent {
    public static final String TYPE = "field.assignment-changed";

    public AgentFieldAssignmentChangedEvent {
        sessionId = normalize(sessionId);
        reason = normalize(reason);
        objectiveId = normalize(objectiveId);
        role = role == null ? AgentFieldRole.ROAMER : role;
        cellIds = cellIds == null ? List.of() : List.copyOf(cellIds);
        regionIds = regionIds == null ? List.of() : List.copyOf(regionIds);
        anchor = anchor == null ? new Point() : new Point(anchor);
        if (agentId <= 0 || occurredAtMs < 0L || mapId <= 0 || sessionId.isEmpty()
                || revision < 0L || partySlot < 0 || cellIds.isEmpty() || regionIds.isEmpty()) {
            throw new IllegalArgumentException("valid field assignment event fields are required");
        }
    }

    @Override
    public Point anchor() {
        return new Point(anchor);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String dedupeKey() {
        return sessionId + ":assignment:" + revision + ':' + agentId;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
