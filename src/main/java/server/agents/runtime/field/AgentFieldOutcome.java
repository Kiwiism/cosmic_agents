package server.agents.runtime.field;

import java.util.Map;

/** Terminal evidence for one managed field visit. */
public record AgentFieldOutcome(
        AgentFieldSessionHandle handle,
        Status status,
        String reason,
        boolean retryable,
        long durationMs,
        long kills,
        int startingLevel,
        int startingExp,
        int endingLevel,
        int endingExp,
        int liveMobsAtExit,
        boolean objectiveComplete,
        Map<Integer, Integer> completedObjectiveKills,
        Map<Integer, Integer> collectedDrops) {
    public AgentFieldOutcome {
        if (handle == null || status == null || durationMs < 0L || kills < 0L
                || startingLevel < 0 || startingExp < 0 || endingLevel < 0 || endingExp < 0
                || liveMobsAtExit < 0) {
            throw new IllegalArgumentException("valid terminal field evidence is required");
        }
        reason = reason == null ? "" : reason.trim();
        completedObjectiveKills = completedObjectiveKills == null
                ? Map.of() : Map.copyOf(completedObjectiveKills);
        collectedDrops = collectedDrops == null ? Map.of() : Map.copyOf(collectedDrops);
    }

    public enum Status { COMPLETED, EXITED, FAILED, CANCELLED }
}
