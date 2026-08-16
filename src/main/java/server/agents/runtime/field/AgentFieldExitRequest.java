package server.agents.runtime.field;

/** Caller-owned request to end one exact field visit. */
public record AgentFieldExitRequest(
        String sessionId,
        String callerId,
        String reason,
        AgentFieldExitMode mode,
        long requestedAtMs,
        long deadlineMs) {
    public AgentFieldExitRequest {
        sessionId = required(sessionId, "field session id");
        callerId = required(callerId, "field caller id");
        reason = reason == null ? "" : reason.trim();
        if (mode == null || requestedAtMs < 0L || deadlineMs < requestedAtMs) {
            throw new IllegalArgumentException("valid field exit timing and mode are required");
        }
    }

    public static AgentFieldExitRequest graceful(
            AgentFieldSessionHandle handle, String reason, long nowMs, long deadlineMs) {
        return new AgentFieldExitRequest(handle.sessionId(), handle.callerId(), reason,
                AgentFieldExitMode.AFTER_CURRENT_ACTION, nowMs, deadlineMs);
    }

    public static AgentFieldExitRequest force(
            AgentFieldSessionHandle handle, String reason, long nowMs) {
        return new AgentFieldExitRequest(handle.sessionId(), handle.callerId(), reason,
                AgentFieldExitMode.FORCE_NOW, nowMs, nowMs);
    }

    private static String required(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " is required");
        return normalized;
    }
}
