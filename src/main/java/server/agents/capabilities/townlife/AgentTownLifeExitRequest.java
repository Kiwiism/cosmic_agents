package server.agents.capabilities.townlife;

/** External request to end one exact TownLife session. */
public record AgentTownLifeExitRequest(
        String sessionId,
        String callerId,
        String reason,
        AgentTownLifeExitMode mode,
        long requestedAtMs,
        long deadlineMs) {

    public AgentTownLifeExitRequest {
        sessionId = normalizeRequired(sessionId, "TownLife session id");
        callerId = normalizeRequired(callerId, "TownLife caller id");
        reason = reason == null ? "" : reason.trim();
        if (mode == null || requestedAtMs < 0L || deadlineMs < requestedAtMs) {
            throw new IllegalArgumentException("valid TownLife exit timing and mode are required");
        }
    }

    public static AgentTownLifeExitRequest graceful(
            AgentTownLifeSessionHandle handle, String reason, long nowMs, long deadlineMs) {
        if (handle == null) {
            throw new IllegalArgumentException("TownLife session handle is required");
        }
        return new AgentTownLifeExitRequest(
                handle.sessionId(), handle.callerId(), reason,
                AgentTownLifeExitMode.AFTER_CURRENT_ACTIVITY, nowMs, deadlineMs);
    }

    public static AgentTownLifeExitRequest force(
            AgentTownLifeSessionHandle handle, String reason, long nowMs) {
        if (handle == null) {
            throw new IllegalArgumentException("TownLife session handle is required");
        }
        return new AgentTownLifeExitRequest(
                handle.sessionId(), handle.callerId(), reason,
                AgentTownLifeExitMode.FORCE_NOW, nowMs, nowMs);
    }

    private static String normalizeRequired(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return normalized;
    }
}
