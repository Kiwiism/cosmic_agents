package server.agents.operations.events;

import server.agents.events.AgentContextualEvent;

/** A recovery action changed or reset Agent movement state. */
public record AgentRecoveryPerformedEvent(
        int agentId,
        long occurredAtMs,
        int mapId,
        String recoveryType,
        int fromX,
        int fromY,
        int toX,
        int toY,
        String objectiveId) implements AgentContextualEvent {
    public static final String TYPE = "recovery.performed";

    public AgentRecoveryPerformedEvent {
        if (agentId <= 0 || occurredAtMs < 0 || mapId < 0
                || recoveryType == null || recoveryType.isBlank()) {
            throw new IllegalArgumentException("Valid recovery context is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }
}
