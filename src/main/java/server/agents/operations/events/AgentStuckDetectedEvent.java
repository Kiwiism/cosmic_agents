package server.agents.operations.events;

import server.agents.events.AgentContextualEvent;

/** Stuck threshold crossed before an unstuck action is requested. */
public record AgentStuckDetectedEvent(
        int agentId,
        long occurredAtMs,
        int mapId,
        int x,
        int y,
        int stuckMs,
        boolean suspended,
        String objectiveId) implements AgentContextualEvent {
    public static final String TYPE = "navigation.stuck-detected";

    public AgentStuckDetectedEvent {
        if (agentId <= 0 || occurredAtMs < 0 || mapId < 0 || stuckMs <= 0) {
            throw new IllegalArgumentException("Valid stuck detection context is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }
}
