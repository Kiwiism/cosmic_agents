package server.agents.progression.questwork;

/** Last authoritative count observed for one catalog objective. */
public record AgentQuestObjectiveProgress(
        String objectiveId,
        int observedCount,
        int requiredCount) {

    public AgentQuestObjectiveProgress {
        objectiveId = objectiveId == null ? "" : objectiveId.trim();
        if (objectiveId.isEmpty() || observedCount < 0 || requiredCount <= 0) {
            throw new IllegalArgumentException("valid objective progress is required");
        }
        observedCount = Math.min(observedCount, requiredCount);
    }

    public boolean complete() {
        return observedCount >= requiredCount;
    }
}
