package server.agents.progression.questwork;

/** Raw quest-attempt facts; negative event timestamps mean the event has not occurred. */
public record AgentQuestAttemptObservation(
        String agentId,
        String workUnitId,
        int questId,
        long attemptStartedAtMs,
        long observedAtMs,
        long lastObjectiveProgressAtMs,
        long lastRelevantDamageAtMs,
        long lastNavigationProgressAtMs,
        long legitimateWaitUntilMs,
        int navigationFailureCount,
        int retryCount,
        int resourceUnitsConsumed,
        int resourceBudget) {

    public AgentQuestAttemptObservation {
        agentId = text(agentId);
        workUnitId = text(workUnitId);
        if (agentId.isEmpty() || workUnitId.isEmpty() || questId <= 0
                || attemptStartedAtMs < 0L || observedAtMs < attemptStartedAtMs
                || invalidEvent(lastObjectiveProgressAtMs, attemptStartedAtMs, observedAtMs)
                || invalidEvent(lastRelevantDamageAtMs, attemptStartedAtMs, observedAtMs)
                || invalidEvent(lastNavigationProgressAtMs, attemptStartedAtMs, observedAtMs)
                || legitimateWaitUntilMs < 0L || navigationFailureCount < 0
                || retryCount < 0 || resourceUnitsConsumed < 0 || resourceBudget < 0) {
            throw new IllegalArgumentException("valid quest attempt observation is required");
        }
    }

    private static boolean invalidEvent(long timestamp, long startedAtMs, long observedAtMs) {
        return timestamp < -1L
                || (timestamp >= 0L && timestamp < startedAtMs)
                || timestamp > observedAtMs;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
