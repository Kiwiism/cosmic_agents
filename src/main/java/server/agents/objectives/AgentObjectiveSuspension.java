package server.agents.objectives;

public record AgentObjectiveSuspension(
        AgentObjectiveDefinition objective,
        String reason,
        long suspendedAtMs) {

    public AgentObjectiveSuspension {
        if (objective == null || reason == null || reason.isBlank() || suspendedAtMs < 0) {
            throw new IllegalArgumentException("objective, reason, and suspension time are required");
        }
        reason = reason.trim();
    }
}
