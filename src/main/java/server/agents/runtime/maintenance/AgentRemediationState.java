package server.agents.runtime.maintenance;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Per-Agent remediation frame; the objective checkpoint remains the durable intent source. */
public final class AgentRemediationState {
    public static final AgentCapabilityStateKey<AgentRemediationState> STATE_KEY =
            new AgentCapabilityStateKey<>("runtime.remediation", AgentRemediationState.class,
                    AgentRemediationState::new);

    private AgentRemediationFrame active;

    public synchronized AgentRemediationFrame active() {
        return active;
    }

    synchronized boolean begin(AgentRemediationFrame frame) {
        if (active != null && !active.frameId().equals(frame.frameId())) {
            return false;
        }
        active = frame;
        return true;
    }

    synchronized boolean clear(String frameId) {
        if (active == null || !active.frameId().equals(frameId)) {
            return false;
        }
        active = null;
        return true;
    }
}
