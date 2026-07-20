package server.agents.progression.events;

import server.agents.events.AgentEvent;

/** Job transition emitted after the server has accepted the advancement. */
public record AgentJobAdvancedEvent(
        int agentId,
        long occurredAtMs,
        int previousJobId,
        int jobId,
        int level,
        int mapId,
        String objectiveId) implements AgentEvent {
    public static final String TYPE = "progression.job-advanced";

    public AgentJobAdvancedEvent {
        if (agentId <= 0 || occurredAtMs < 0 || previousJobId < 0 || jobId < 0
                || previousJobId == jobId || level < 1 || mapId < -1) {
            throw new IllegalArgumentException("Valid job advancement context is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String dedupeKey() {
        return previousJobId + ":" + jobId;
    }
}
