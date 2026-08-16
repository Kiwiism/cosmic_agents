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
        String sessionId,
        String requestId,
        String callerId,
        long sessionStartedAtMs,
        boolean suspended,
        AgentPlanExitMode pendingExitMode,
        String pendingExitReason,
        long pendingExitDeadlineMs,
        long stateRevision,
        long updatedAtMs) {

    public AgentPlanCheckpoint {
        if (schemaVersion <= 0 || characterId <= 0 || planId == null || planId.isBlank()
                || planVersion == null || planVersion.isBlank() || chainId == null
                || chainId.isBlank() || stepIndex < 0 || stepAttempt < 0
                || stepStartedAtMs < 0 || status == null || nextActionAtMs < 0
                || sessionStartedAtMs < 0L || pendingExitDeadlineMs < 0L
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
        sessionId = sessionId == null ? "" : sessionId.trim();
        requestId = requestId == null ? "" : requestId.trim();
        callerId = callerId == null ? "" : callerId.trim();
        pendingExitReason = pendingExitReason == null ? "" : pendingExitReason.trim();
        if (!sessionId.isEmpty() && (requestId.isEmpty() || callerId.isEmpty())) {
            throw new IllegalArgumentException("persisted plan ownership identity is incomplete");
        }
    }

    /** Source-compatible constructor for schema-v1 tests and migration tooling. */
    public AgentPlanCheckpoint(
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
        this(schemaVersion, characterId, planId, planVersion, chainId, stepIndex, stepStarted,
                stepAttempt, stepStartedAtMs, status, inputs, pendingSuccessorPlanId,
                availableSuccessorPlanIds, deferredSuccessorPlanId, nextActionAtMs, reason,
                "", "", "", 0L, false, null, "", 0L, stateRevision, updatedAtMs);
    }
}
