package server.agents.operations.events;

import server.agents.events.AgentContextualEvent;

/** Combat target identity changed at the grind-target boundary. */
public record AgentCombatTargetChangedEvent(
        int agentId,
        long occurredAtMs,
        int previousObjectId,
        int targetObjectId,
        int targetMobId,
        int switchCount,
        String objectiveId) implements AgentContextualEvent {
    public static final String TYPE = "combat.target-changed";

    public AgentCombatTargetChangedEvent {
        if (agentId <= 0 || occurredAtMs < 0 || previousObjectId < 0 || targetObjectId < 0
                || targetMobId < 0 || switchCount < 0
                || (previousObjectId == targetObjectId && targetObjectId != 0)) {
            throw new IllegalArgumentException("Valid combat target transition is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }
}
