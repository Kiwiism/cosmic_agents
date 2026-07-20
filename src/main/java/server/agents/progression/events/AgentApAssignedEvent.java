package server.agents.progression.events;

import server.agents.events.AgentEvent;

/** Aggregate AP allocation fact; one event is emitted for one assignment operation. */
public record AgentApAssignedEvent(
        int agentId,
        long occurredAtMs,
        int level,
        int str,
        int dex,
        int intelligence,
        int luk,
        int remainingAp,
        String profileId,
        String objectiveId) implements AgentEvent {
    public static final String TYPE = "progression.ap-assigned";

    public AgentApAssignedEvent {
        if (agentId <= 0 || occurredAtMs < 0 || level < 1 || str < 0 || dex < 0
                || intelligence < 0 || luk < 0 || str + dex + intelligence + luk < 1
                || remainingAp < 0) {
            throw new IllegalArgumentException("Valid AP allocation context is required");
        }
        profileId = profileId == null ? "" : profileId;
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }
}
