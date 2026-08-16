package server.agents.runtime.field;

/** Durable external field schedule, separate from the local field checkpoint. */
record AgentFieldVisitLeaseCheckpoint(
        int schemaVersion,
        int characterId,
        String sessionId,
        String requestId,
        String callerId,
        int mapId,
        long startedAtMs,
        long exitAtMs,
        long gracefulTimeoutMs,
        String exitReason,
        boolean exitRequested) {
    AgentFieldVisitLeaseCheckpoint {
        sessionId = normalize(sessionId);
        requestId = normalize(requestId);
        callerId = normalize(callerId);
        exitReason = exitReason == null ? "" : exitReason.trim();
        if (schemaVersion != 1 || characterId <= 0 || mapId <= 0
                || sessionId.isEmpty() || requestId.isEmpty() || callerId.isEmpty()
                || startedAtMs < 0L || exitAtMs <= 0L || gracefulTimeoutMs <= 0L) {
            throw new IllegalArgumentException("valid field visit lease checkpoint is required");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
