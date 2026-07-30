package server.agents.capabilities.looting;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Short-lived objective handoff gate used to finish a nearby loot batch. */
public final class AgentPreExitLootState {
    public static final AgentCapabilityStateKey<AgentPreExitLootState> STATE_KEY =
            new AgentCapabilityStateKey<>("looting.pre-exit",
                    AgentPreExitLootState.class, AgentPreExitLootState::new);

    private long deadlineMs;

    public synchronized void begin(long nowMs) {
        if (deadlineMs <= 0L) {
            deadlineMs = nowMs + AgentLootCollectionPolicyConfig.preExitMaxWaitMs();
        }
    }

    public synchronized boolean active(long nowMs) {
        return deadlineMs > nowMs;
    }

    public synchronized void clear() {
        deadlineMs = 0L;
    }
}
