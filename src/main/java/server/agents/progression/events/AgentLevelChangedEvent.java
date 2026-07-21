package server.agents.progression.events;

import server.agents.events.AgentContextualEvent;

/** Authoritative level transition observed by the Agent build runtime. */
public record AgentLevelChangedEvent(
        int agentId,
        long occurredAtMs,
        int previousLevel,
        int level,
        int jobId,
        int mapId,
        String objectiveId) implements AgentContextualEvent {
    public static final String TYPE = "progression.level-changed";

    public AgentLevelChangedEvent {
        if (agentId <= 0 || occurredAtMs < 0 || previousLevel < 1 || level < 1
                || previousLevel == level || jobId < 0 || mapId < -1) {
            throw new IllegalArgumentException("Valid level transition context is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String dedupeKey() {
        return previousLevel + ":" + level;
    }
}
