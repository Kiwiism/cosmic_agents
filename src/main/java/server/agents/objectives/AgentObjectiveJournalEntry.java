package server.agents.objectives;

public record AgentObjectiveJournalEntry(
        long timestampMs,
        String objectiveId,
        AgentObjectiveStatus status,
        String reason,
        AgentObjectiveSource source,
        String behaviorVersion,
        String correlationId) {
}
