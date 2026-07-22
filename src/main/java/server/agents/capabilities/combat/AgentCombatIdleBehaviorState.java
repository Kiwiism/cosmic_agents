package server.agents.capabilities.combat;

import server.agents.runtime.state.AgentCapabilityStateKey;

public final class AgentCombatIdleBehaviorState {
    public static final AgentCapabilityStateKey<AgentCombatIdleBehaviorState> STATE_KEY =
            new AgentCapabilityStateKey<>("combat.idle-behavior", AgentCombatIdleBehaviorState.class,
                    AgentCombatIdleBehaviorState::new);

    private long nextDecisionAtMs;

    public synchronized boolean due(long nowMs) { return nowMs >= nextDecisionAtMs; }
    public synchronized void defer(long nowMs, int durationMs) { nextDecisionAtMs = nowMs + durationMs; }
}
