package server.agents.resources.events;

import server.agents.events.AgentContextualEvent;

/** Ground loot collected by the Agent, including mesos. */
public record AgentLootCollectedEvent(
        int agentId,
        long occurredAtMs,
        int mapId,
        int mapObjectId,
        int itemId,
        int quantity,
        int mesos,
        String objectiveId) implements AgentContextualEvent {
    public static final String TYPE = "loot.collected";

    public AgentLootCollectedEvent {
        if (agentId <= 0 || occurredAtMs < 0 || mapId < -1 || mapObjectId <= 0
                || itemId < 0 || quantity < 0 || mesos < 0
                || (itemId == 0 && mesos == 0)) {
            throw new IllegalArgumentException("Valid collected loot context is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String dedupeKey() {
        return String.valueOf(mapObjectId);
    }
}
