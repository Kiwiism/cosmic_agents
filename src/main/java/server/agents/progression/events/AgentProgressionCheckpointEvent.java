package server.agents.progression.events;

import server.agents.events.AgentEvent;

/** Durable career state successfully written to the progression checkpoint store. */
public record AgentProgressionCheckpointEvent(
        int agentId,
        long occurredAtMs,
        String bundleId,
        int bundleVersion,
        String stage,
        long stateRevision,
        int level,
        int jobId,
        int mapId,
        String objectiveId) implements AgentEvent {
    public static final String TYPE = "progression.checkpoint-reached";

    public AgentProgressionCheckpointEvent {
        if (agentId <= 0 || occurredAtMs < 0 || bundleId == null || bundleId.isBlank()
                || bundleVersion <= 0 || stage == null || stage.isBlank() || stateRevision < 0
                || level < 1 || jobId < 0 || mapId < -1) {
            throw new IllegalArgumentException("Valid progression checkpoint context is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String dedupeKey() {
        return bundleId + ":" + stateRevision;
    }
}
