package server.agents.capabilities.combat;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Acquisition timing state. Target commitment remains owned by combat. */
public final class AgentCombatBehaviorState {
    public static final AgentCapabilityStateKey<AgentCombatBehaviorState> STATE_KEY =
            new AgentCapabilityStateKey<>("combat.behavior", AgentCombatBehaviorState.class,
                    AgentCombatBehaviorState::new);

    private long stimulus;
    private long readyAtMs;
    private boolean responseDeferred;

    public synchronized boolean ready(long newStimulus, long nowMs, int delayMs) {
        if (newStimulus != stimulus) {
            stimulus = newStimulus;
            readyAtMs = nowMs + Math.max(0, delayMs);
        }
        responseDeferred = nowMs < readyAtMs;
        return !responseDeferred;
    }

    public synchronized void clearStimulus() {
        stimulus = 0L;
        readyAtMs = 0L;
        responseDeferred = false;
    }

    public synchronized boolean responseDeferred() { return responseDeferred; }
}
