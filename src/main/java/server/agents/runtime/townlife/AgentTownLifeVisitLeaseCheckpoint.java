package server.agents.runtime.townlife;

/** Durable external schedule; separate from TownLife's local activity checkpoint. */
record AgentTownLifeVisitLeaseCheckpoint(
        int schemaVersion,
        int characterId,
        String sessionId,
        String requestId,
        String callerId,
        int townMapId,
        long startedAtMs,
        long exitAtMs,
        long gracefulTimeoutMs,
        String exitReason) {

    AgentTownLifeVisitLeaseCheckpoint {
        sessionId = normalize(sessionId);
        requestId = normalize(requestId);
        callerId = normalize(callerId);
        exitReason = exitReason == null ? "" : exitReason.trim();
        if (schemaVersion != 1 || characterId <= 0 || townMapId <= 0
                || sessionId.isEmpty() || requestId.isEmpty() || callerId.isEmpty()
                || startedAtMs < 0L || exitAtMs <= 0L || gracefulTimeoutMs <= 0L) {
            throw new IllegalArgumentException("valid TownLife visit lease checkpoint is required");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
