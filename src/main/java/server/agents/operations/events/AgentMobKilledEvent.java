package server.agents.operations.events;

import server.agents.events.AgentEvent;

/** A monster kill was credited to an Agent at the authoritative map boundary. */
public record AgentMobKilledEvent(
        int agentId,
        long occurredAtMs,
        int mapId,
        int mobId,
        int mobObjectId,
        int mobLevel,
        String objectiveId) implements AgentEvent {
    public static final String TYPE = "combat.mob-killed";

    public AgentMobKilledEvent {
        if (agentId <= 0 || occurredAtMs < 0 || mapId < 0 || mobId <= 0
                || mobObjectId <= 0 || mobLevel < 0) {
            throw new IllegalArgumentException("Valid Agent kill context is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String dedupeKey() {
        return String.valueOf(mobObjectId);
    }
}
