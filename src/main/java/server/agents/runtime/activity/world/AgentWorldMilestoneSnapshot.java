package server.agents.runtime.activity.world;

import java.util.EnumMap;
import java.util.Map;

/** Explainable, immutable milestone projection. */
public record AgentWorldMilestoneSnapshot(
        long capturedAtMs,
        Map<AgentWorldMilestone, AgentWorldMilestoneStatus> statuses,
        Map<AgentWorldMilestone, String> evidence) {

    public AgentWorldMilestoneSnapshot {
        if (capturedAtMs < 0L) {
            throw new IllegalArgumentException("valid milestone capture time is required");
        }
        EnumMap<AgentWorldMilestone, AgentWorldMilestoneStatus> complete =
                new EnumMap<>(AgentWorldMilestone.class);
        complete.putAll(statuses == null ? Map.of() : statuses);
        for (AgentWorldMilestone milestone : AgentWorldMilestone.values()) {
            complete.putIfAbsent(milestone, AgentWorldMilestoneStatus.UNKNOWN);
        }
        statuses = Map.copyOf(complete);
        evidence = Map.copyOf(evidence == null ? Map.of() : evidence);
    }

    public AgentWorldMilestoneStatus status(AgentWorldMilestone milestone) {
        return statuses.getOrDefault(milestone, AgentWorldMilestoneStatus.UNKNOWN);
    }

    public boolean achieved(AgentWorldMilestone milestone) {
        return status(milestone) == AgentWorldMilestoneStatus.ACHIEVED;
    }
}
