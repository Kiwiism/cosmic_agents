package server.agents.operations.events;

import server.agents.events.AgentEvent;

/** Agent entered a dead window or completed respawn recovery. */
public record AgentLifeStateChangedEvent(
        int agentId,
        long occurredAtMs,
        String previousState,
        String state,
        int mapId,
        String objectiveId) implements AgentEvent {
    public static final String TYPE = "combat.life-state-changed";

    public AgentLifeStateChangedEvent {
        if (agentId <= 0 || occurredAtMs < 0 || previousState == null || previousState.isBlank()
                || state == null || state.isBlank() || previousState.equals(state) || mapId < 0) {
            throw new IllegalArgumentException("Valid life-state transition is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }
}
