package server.agents.plans;

import java.util.List;
import java.util.Map;

/** Terminal plan evidence returned to an external selector/coordinator. */
public record AgentPlanOutcome(
        AgentPlanSessionPhase phase,
        AgentPlanSessionHandle handle,
        String reason,
        boolean retryable,
        int lastStepIndex,
        Map<String, Object> inputs,
        List<String> suggestedSuccessorPlanIds,
        long endedAtMs) {
    public AgentPlanOutcome {
        if (phase == null || !phase.terminal() || endedAtMs < 0L) {
            throw new IllegalArgumentException("terminal plan phase and valid end timing are required");
        }
        reason = reason == null ? "" : reason.trim();
        inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
        suggestedSuccessorPlanIds = suggestedSuccessorPlanIds == null
                ? List.of() : List.copyOf(suggestedSuccessorPlanIds);
    }
}
