package server.agents.operations.events;

import server.agents.events.AgentEvent;

/** Navigation could not produce a route between two distinct valid regions. */
public record AgentNavigationRouteFailedEvent(
        int agentId,
        long occurredAtMs,
        int mapId,
        int startRegionId,
        int targetRegionId,
        int targetX,
        int targetY,
        String reason,
        String objectiveId) implements AgentEvent {
    public static final String TYPE = "navigation.route-failed";

    public AgentNavigationRouteFailedEvent {
        if (agentId <= 0 || occurredAtMs < 0 || mapId < 0 || startRegionId < 0
                || targetRegionId < 0 || startRegionId == targetRegionId
                || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Valid failed route context is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String dedupeKey() {
        return mapId + ":" + startRegionId + ":" + targetRegionId + ":" + targetX + ":" + targetY;
    }
}
