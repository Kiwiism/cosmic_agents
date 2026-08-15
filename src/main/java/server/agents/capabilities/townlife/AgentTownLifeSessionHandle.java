package server.agents.capabilities.townlife;

/** Stable correlation identity returned to the external owner of a TownLife session. */
public record AgentTownLifeSessionHandle(
        String sessionId,
        String requestId,
        String callerId,
        int agentId,
        int townMapId,
        long startedAtMs) {

    public AgentTownLifeSessionHandle {
        sessionId = normalize(sessionId);
        requestId = normalize(requestId);
        callerId = normalize(callerId);
        if (sessionId.isEmpty() || requestId.isEmpty() || callerId.isEmpty()
                || agentId <= 0 || townMapId <= 0 || startedAtMs < 0L) {
            throw new IllegalArgumentException("valid TownLife session identity is required");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
