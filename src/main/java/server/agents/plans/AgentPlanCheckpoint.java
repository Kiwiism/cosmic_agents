package server.agents.plans;

import java.util.List;
import java.util.Map;

/** Durable orchestration cursor. Capability-specific transient attachments are never persisted. */
public record AgentPlanCheckpoint(
        int schemaVersion,
        int characterId,
        String planId,
        String planVersion,
        String chainId,
        int stepIndex,
        boolean stepStarted,
        int stepAttempt,
        long stepStartedAtMs,
        AgentPlanExecutionStatus status,
        Map<String, Object> inputs,
        String pendingSuccessorPlanId,
        List<String> availableSuccessorPlanIds,
        String deferredSuccessorPlanId,
        long nextActionAtMs,
        String reason,
        long stateRevision,
        long updatedAtMs) {

    public AgentPlanCheckpoint {
        if (schemaVersion <= 0 || characterId <= 0 || planId == null || planId.isBlank()
                || planVersion == null || planVersion.isBlank() || chainId == null
                || chainId.isBlank() || stepIndex < 0 || stepAttempt < 0
                || stepStartedAtMs < 0 || status == null || nextActionAtMs < 0
                || stateRevision < 0 || updatedAtMs < 0) {
            throw new IllegalArgumentException("complete universal plan checkpoint is required");
        }
        planId = planId.trim();
        planVersion = planVersion.trim();
        chainId = chainId.trim();
        inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
        pendingSuccessorPlanId =
                pendingSuccessorPlanId == null ? "" : pendingSuccessorPlanId.trim();
        availableSuccessorPlanIds =
                availableSuccessorPlanIds == null ? List.of() : List.copyOf(availableSuccessorPlanIds);
        deferredSuccessorPlanId =
                deferredSuccessorPlanId == null ? "" : deferredSuccessorPlanId.trim();
        reason = reason == null ? "" : reason;
    }
}
