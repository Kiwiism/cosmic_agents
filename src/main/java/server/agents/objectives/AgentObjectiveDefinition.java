package server.agents.objectives;

public record AgentObjectiveDefinition(
        String objectiveId,
        String type,
        int priority,
        long deadlineMs,
        int retryBudget,
        AgentObjectiveSource source,
        String behaviorVersion,
        String correlationId) {

    public AgentObjectiveDefinition {
        if (objectiveId == null || objectiveId.isBlank() || type == null || type.isBlank()
                || priority < 0 || deadlineMs < 0 || retryBudget < 0 || source == null
                || behaviorVersion == null || behaviorVersion.isBlank()) {
            throw new IllegalArgumentException("Valid objective identity, policy, and bounds are required");
        }
        objectiveId = objectiveId.trim();
        type = type.trim();
        behaviorVersion = behaviorVersion.trim();
        correlationId = correlationId == null || correlationId.isBlank()
                ? objectiveId : correlationId.trim();
    }
}
