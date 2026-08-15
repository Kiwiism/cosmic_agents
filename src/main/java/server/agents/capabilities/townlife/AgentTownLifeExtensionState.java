package server.agents.capabilities.townlife;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Transient execution state for one bounded local activity extension. */
final class AgentTownLifeExtensionState {
    static final AgentCapabilityStateKey<AgentTownLifeExtensionState> STATE_KEY =
            new AgentCapabilityStateKey<>(
                    "town-life.extension", AgentTownLifeExtensionState.class,
                    AgentTownLifeExtensionState::new);

    private String handlerId = "";
    private boolean started;
    private long deadlineMs;

    synchronized void prepare(String nextHandlerId, long nextDeadlineMs) {
        handlerId = nextHandlerId == null ? "" : nextHandlerId.trim();
        started = false;
        deadlineMs = Math.max(0L, nextDeadlineMs);
    }

    synchronized void markStarted() {
        started = true;
    }

    synchronized String handlerId() {
        return handlerId;
    }

    synchronized boolean started() {
        return started;
    }

    synchronized long deadlineMs() {
        return deadlineMs;
    }

    synchronized void clear() {
        handlerId = "";
        started = false;
        deadlineMs = 0L;
    }
}
