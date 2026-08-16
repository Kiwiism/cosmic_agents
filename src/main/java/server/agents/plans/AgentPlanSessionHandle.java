package server.agents.plans;

/** Stable plan ownership identity persisted with its checkpoint cursor. */
public record AgentPlanSessionHandle(
        String sessionId,
        String requestId,
        String callerId,
        int characterId,
        String planId,
        long startedAtMs) {
    public AgentPlanSessionHandle {
        sessionId = required(sessionId, "plan session id");
        requestId = required(requestId, "plan request id");
        callerId = required(callerId, "plan caller id");
        planId = required(planId, "plan id");
        if (characterId < 0 || startedAtMs < 0L) {
            throw new IllegalArgumentException("valid plan Agent identity and timing are required");
        }
    }

    private static String required(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " is required");
        return normalized;
    }
}
