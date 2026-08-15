package server.agents.operations.events;

import server.agents.events.AgentContextualEvent;

/** Positive HP damage accepted by the authoritative map for an Agent attack. */
public record AgentMobDamagedEvent(
        int agentId,
        long occurredAtMs,
        int mapId,
        int mobId,
        int mobObjectId,
        int appliedDamage,
        String objectiveId) implements AgentContextualEvent {
    public static final String TYPE = "combat.mob-damaged";

    public AgentMobDamagedEvent {
        if (agentId <= 0 || occurredAtMs < 0 || mapId < 0 || mobId <= 0
                || mobObjectId <= 0 || appliedDamage <= 0) {
            throw new IllegalArgumentException("Valid Agent damage context is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }
}
