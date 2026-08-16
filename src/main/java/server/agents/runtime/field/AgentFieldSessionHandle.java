package server.agents.runtime.field;

/** Stable handle callers must present when requesting exit. */
public record AgentFieldSessionHandle(
        String sessionId,
        String requestId,
        String callerId,
        int characterId,
        int mapId,
        long startedAtMs) {
    public AgentFieldSessionHandle {
        sessionId = required(sessionId, "field session id");
        requestId = required(requestId, "field request id");
        callerId = required(callerId, "field caller id");
        if (characterId <= 0 || mapId <= 0 || startedAtMs < 0L) {
            throw new IllegalArgumentException("valid field session identity is required");
        }
    }

    private static String required(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " is required");
        return normalized;
    }
}
