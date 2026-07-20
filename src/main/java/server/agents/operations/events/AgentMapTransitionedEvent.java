package server.agents.operations.events;

import server.agents.events.AgentEvent;

/** Agent completed an inter-map or intra-map portal/map transition. */
public record AgentMapTransitionedEvent(
        int agentId,
        long occurredAtMs,
        int previousMapId,
        int mapId,
        int portalId,
        String reason,
        String objectiveId) implements AgentEvent {
    public static final String TYPE = "navigation.map-transitioned";

    public AgentMapTransitionedEvent {
        if (agentId <= 0 || occurredAtMs < 0 || previousMapId < 0 || mapId < 0
                || portalId < -1 || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Valid map transition context is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }
}
