package server.agents.capabilities.presentation;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Bounded, session-local decision state. At most one cosmetic intent is pending. */
public final class AgentPresentationState {
    public static final AgentCapabilityStateKey<AgentPresentationState> STATE_KEY =
            new AgentCapabilityStateKey<>("personality.presentation",
                    AgentPresentationState.class, AgentPresentationState::new);

    private long decisionSequence;
    private AgentPresentationDecision pending;
    private int lastObserverCount = -1;

    public synchronized long nextDecisionSequence() {
        return ++decisionSequence;
    }

    public synchronized boolean schedule(AgentPresentationDecision decision) {
        if (decision == null || pending != null) {
            return false;
        }
        pending = decision;
        return true;
    }

    public synchronized AgentPresentationDecision takeDue(long nowMs) {
        if (pending == null || nowMs < pending.notBeforeMs()) {
            return null;
        }
        AgentPresentationDecision due = pending;
        pending = null;
        return due;
    }

    public synchronized boolean observerBecamePresent(int observerCount) {
        int normalized = Math.max(0, observerCount);
        boolean becamePresent = normalized > 0 && lastObserverCount <= 0;
        lastObserverCount = normalized;
        return becamePresent;
    }

    public synchronized void clear() {
        pending = null;
        lastObserverCount = -1;
    }
}
