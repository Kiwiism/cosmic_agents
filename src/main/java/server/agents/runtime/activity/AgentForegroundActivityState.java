package server.agents.runtime.activity;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Session-local provenance for foreground activity ownership. */
public final class AgentForegroundActivityState {
    public static final AgentCapabilityStateKey<AgentForegroundActivityState> STATE_KEY =
            new AgentCapabilityStateKey<>("runtime.foreground-activity",
                    AgentForegroundActivityState.class, AgentForegroundActivityState::new);

    private String activityId;
    private String previousActivityId;
    private long enteredAtMs;
    private long transitionCount;

    public synchronized void select(String nextActivityId, long nowMs) {
        if (nextActivityId == null || nextActivityId.isBlank()) {
            clear(nowMs);
            return;
        }
        if (nextActivityId.equals(activityId)) {
            return;
        }
        previousActivityId = activityId;
        activityId = nextActivityId;
        enteredAtMs = nowMs;
        transitionCount++;
    }

    public synchronized void clear(long nowMs) {
        if (activityId == null) {
            return;
        }
        previousActivityId = activityId;
        activityId = null;
        enteredAtMs = nowMs;
        transitionCount++;
    }

    public synchronized String activityId() {
        return activityId;
    }

    public synchronized String previousActivityId() {
        return previousActivityId;
    }

    public synchronized long enteredAtMs() {
        return enteredAtMs;
    }

    public synchronized long transitionCount() {
        return transitionCount;
    }
}
