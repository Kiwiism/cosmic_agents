package server.agents.capabilities.supplies;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Run-scoped guard used when an Agent must prove it can sustain itself without transfers. */
public final class AgentResourceAutonomyState {
    public static final AgentCapabilityStateKey<AgentResourceAutonomyState> STATE_KEY =
            new AgentCapabilityStateKey<>("supplies.resource-autonomy",
                    AgentResourceAutonomyState.class, AgentResourceAutonomyState::new);

    private boolean selfSustaining;

    public synchronized boolean selfSustaining() {
        return selfSustaining;
    }

    public synchronized void requireSelfSustaining() {
        selfSustaining = true;
    }

    public synchronized void clear() {
        selfSustaining = false;
    }
}
