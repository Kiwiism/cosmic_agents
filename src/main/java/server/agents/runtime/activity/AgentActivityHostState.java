package server.agents.runtime.activity;

import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.state.AgentCapabilityStateKey;

/** Session-local provenance for the current Activity Host execution owner. */
public final class AgentActivityHostState {
    public static final AgentCapabilityStateKey<AgentActivityHostState> STATE_KEY =
            new AgentCapabilityStateKey<>("runtime.foreground-activity",
                    AgentActivityHostState.class, AgentActivityHostState::new);

    private String controllerId;
    private String previousControllerId;
    private AgentActivityKind activityKind;
    private long enteredAtMs;
    private long transitionCount;

    public synchronized boolean select(
            String nextControllerId, AgentActivityKind nextKind, long nowMs) {
        if (nextControllerId == null || nextControllerId.isBlank()) {
            return clear(nowMs);
        }
        if (nextControllerId.equals(controllerId)) {
            return false;
        }
        previousControllerId = controllerId;
        controllerId = nextControllerId;
        activityKind = nextKind;
        enteredAtMs = nowMs;
        transitionCount++;
        return true;
    }

    public synchronized boolean clear(long nowMs) {
        if (controllerId == null) {
            return false;
        }
        previousControllerId = controllerId;
        controllerId = null;
        activityKind = null;
        enteredAtMs = nowMs;
        transitionCount++;
        return true;
    }

    public synchronized String controllerId() { return controllerId; }
    public synchronized String previousControllerId() { return previousControllerId; }
    public synchronized AgentActivityKind activityKind() { return activityKind; }
    public synchronized long enteredAtMs() { return enteredAtMs; }
    public synchronized long transitionCount() { return transitionCount; }
}
