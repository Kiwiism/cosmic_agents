package server.agents.capabilities.dialogue.semantic;

public record AgentDialogueRuntimeSnapshot(
        long semanticActs,
        long projectionRequests,
        long projected,
        long topicSuppressed,
        long noAudienceSuppressed,
        long cooldownSuppressed,
        long mapBudgetSuppressed,
        long sessionsStarted,
        long sessionsCompleted,
        long sessionsTimedOut,
        long coordinationPublished,
        long coordinationDelivered,
        long failures,
        int activeSessions) {
}
