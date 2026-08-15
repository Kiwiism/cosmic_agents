package server.agents.capabilities.townlife;

/** Durable local-session intent. Transient destinations and live map objects are never stored. */
public record AgentTownLifeCheckpoint(int schemaVersion,
                                      int characterId,
                                      int townMapId,
                                      AgentTownLifeVisitRequest.Purpose purpose,
                                      String reason,
                                      long remainingFreeTimeMs,
                                      long updatedAtMs,
                                      String sessionId,
                                      String requestId,
                                      String callerId,
                                      boolean exitRequested,
                                      AgentTownLifeExitMode exitMode,
                                      String exitReason,
                                      long remainingExitDeadlineMs,
                                      AgentTownLifeState.Activity currentActivity,
                                      AgentTownLifeActivityResult activityResult) {
    public AgentTownLifeCheckpoint {
        if ((schemaVersion != 1 && schemaVersion != 2)
                || characterId <= 0 || townMapId <= 0 || purpose == null
                || remainingFreeTimeMs < 0L || updatedAtMs < 0L
                || remainingExitDeadlineMs < 0L) {
            throw new IllegalArgumentException("valid TownLife checkpoint identity is required");
        }
        reason = reason == null ? "" : reason;
        sessionId = normalize(sessionId);
        requestId = normalize(requestId);
        callerId = normalize(callerId);
        exitMode = exitMode == null
                ? AgentTownLifeExitMode.AFTER_CURRENT_ACTIVITY : exitMode;
        exitReason = normalize(exitReason);
        currentActivity = currentActivity == null
                ? AgentTownLifeState.Activity.NONE : currentActivity;
        activityResult = activityResult == null
                ? AgentTownLifeActivityResult.NONE : activityResult;
        if (schemaVersion == 2
                && (sessionId.isEmpty() || requestId.isEmpty() || callerId.isEmpty())) {
            throw new IllegalArgumentException("TownLife checkpoint v2 session identity is required");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
