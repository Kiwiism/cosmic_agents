package server.agents.progression.events;

import server.agents.events.AgentContextualEvent;

/** Authoritative quest kill progress crossing emitted after the live counter changes. */
public record AgentQuestProgressMilestoneEvent(
        int agentId,
        long occurredAtMs,
        int questId,
        int targetId,
        int currentCount,
        int requiredCount,
        int milestonePercent,
        int mapId,
        String objectiveId) implements AgentContextualEvent {
    public static final String TYPE = "progression.quest-progress-milestone";

    public AgentQuestProgressMilestoneEvent {
        if (agentId <= 0 || occurredAtMs < 0 || questId <= 0 || targetId <= 0
                || currentCount <= 0 || requiredCount <= 0 || currentCount > requiredCount
                || milestonePercent <= 0 || milestonePercent >= 100 || mapId < -1) {
            throw new IllegalArgumentException("Valid quest progress milestone context is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String dedupeKey() {
        return questId + ":" + targetId + ":" + milestonePercent;
    }
}
