package server.agents.catalog.decision;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Per-session sampling state prevents advisory catalog queries from running on every live tick. */
public final class AgentDecisionShadowSamplingState {
    public static final AgentCapabilityStateKey<AgentDecisionShadowSamplingState> STATE_KEY =
            new AgentCapabilityStateKey<>("catalog.decision-shadow-sampling",
                    AgentDecisionShadowSamplingState.class,
                    AgentDecisionShadowSamplingState::new);

    private long nextNavigationAtMs;
    private long nextCombatAtMs;

    public synchronized boolean allowNavigation(long nowMs, long intervalMs) {
        if (nowMs < nextNavigationAtMs) {
            return false;
        }
        nextNavigationAtMs = nowMs + intervalMs;
        return true;
    }

    public synchronized boolean allowCombat(long nowMs, long intervalMs) {
        if (nowMs < nextCombatAtMs) {
            return false;
        }
        nextCombatAtMs = nowMs + intervalMs;
        return true;
    }
}
