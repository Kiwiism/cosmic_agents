package server.agents.progression.events;

import server.agents.events.AgentEvent;

/** Quest lifecycle fact emitted only after the live quest status changes. */
public record AgentQuestStateChangedEvent(
        int agentId,
        long occurredAtMs,
        int questId,
        int previousStatus,
        int status,
        int npcId,
        int mapId,
        Integer rewardSelection,
        String objectiveId) implements AgentEvent {
    public static final String TYPE = "progression.quest-state-changed";

    public AgentQuestStateChangedEvent {
        if (agentId <= 0 || occurredAtMs < 0 || questId <= 0 || previousStatus < 0
                || status < 0 || previousStatus == status || npcId < 0 || mapId < -1) {
            throw new IllegalArgumentException("Valid quest transition context is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String dedupeKey() {
        return questId + ":" + status;
    }
}
