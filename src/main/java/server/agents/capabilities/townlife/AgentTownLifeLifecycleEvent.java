package server.agents.capabilities.townlife;

import server.agents.events.AgentContextualEvent;

/** Correlated fact describing the externally-owned TownLife session lifecycle. */
public record AgentTownLifeLifecycleEvent(
        int agentId,
        long occurredAtMs,
        int mapId,
        String sessionId,
        String requestId,
        String callerId,
        Phase phase,
        String reason,
        AgentTownLifeState.Activity finalActivity,
        AgentTownLifeActivityResult activityResult) implements AgentContextualEvent {

    public static final String TYPE = "townlife.lifecycle";

    public AgentTownLifeLifecycleEvent {
        sessionId = normalize(sessionId);
        requestId = normalize(requestId);
        callerId = normalize(callerId);
        reason = normalize(reason);
        finalActivity = finalActivity == null ? AgentTownLifeState.Activity.NONE : finalActivity;
        activityResult = activityResult == null ? AgentTownLifeActivityResult.NONE : activityResult;
        if (agentId <= 0 || occurredAtMs < 0L || mapId <= 0 || sessionId.isEmpty()
                || requestId.isEmpty() || callerId.isEmpty() || phase == null) {
            throw new IllegalArgumentException("valid TownLife lifecycle event fields are required");
        }
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String objectiveId() {
        return "";
    }

    @Override
    public String dedupeKey() {
        return sessionId + ':' + phase;
    }

    public enum Phase {
        STARTED,
        EXIT_REQUESTED,
        EXITED,
        FORCED,
        TIMED_OUT
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
