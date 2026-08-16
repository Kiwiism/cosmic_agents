package server.agents.plans;

/** Caller-authorized suspend or exit request for an exact plan session. */
public record AgentPlanExitRequest(
        String sessionId,
        String callerId,
        AgentPlanExitMode mode,
        String reason,
        long requestedAtMs,
        long deadlineMs) {
    public AgentPlanExitRequest {
        sessionId = required(sessionId, "plan session id");
        callerId = required(callerId, "plan caller id");
        reason = reason == null ? "" : reason.trim();
        if (mode == null || requestedAtMs < 0L || deadlineMs < requestedAtMs) {
            throw new IllegalArgumentException("valid plan exit mode and timing are required");
        }
    }

    public static AgentPlanExitRequest suspend(
            AgentPlanSessionHandle handle, String reason, long nowMs, long deadlineMs) {
        return new AgentPlanExitRequest(handle.sessionId(), handle.callerId(),
                AgentPlanExitMode.SUSPEND_AFTER_STEP, reason, nowMs, deadlineMs);
    }

    public static AgentPlanExitRequest graceful(
            AgentPlanSessionHandle handle, String reason, long nowMs, long deadlineMs) {
        return new AgentPlanExitRequest(handle.sessionId(), handle.callerId(),
                AgentPlanExitMode.EXIT_AFTER_STEP, reason, nowMs, deadlineMs);
    }

    public static AgentPlanExitRequest force(
            AgentPlanSessionHandle handle, String reason, long nowMs) {
        return new AgentPlanExitRequest(handle.sessionId(), handle.callerId(),
                AgentPlanExitMode.FORCE_NOW, reason, nowMs, nowMs);
    }

    private static String required(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " is required");
        return normalized;
    }
}
