package server.agents.operations.events;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Dedupe state for presentation-only combat posture transitions. */
public final class AgentCombatPostureState {
    public static final AgentCapabilityStateKey<AgentCombatPostureState> STATE_KEY =
            new AgentCapabilityStateKey<>("combat.posture-observation",
                    AgentCombatPostureState.class, AgentCombatPostureState::new);

    private AgentCombatPostureChangedEvent.Posture posture =
            AgentCombatPostureChangedEvent.Posture.IDLE;

    public synchronized boolean transition(AgentCombatPostureChangedEvent.Posture next) {
        AgentCombatPostureChangedEvent.Posture normalized = next == null
                ? AgentCombatPostureChangedEvent.Posture.IDLE : next;
        if (posture == normalized) {
            return false;
        }
        posture = normalized;
        return true;
    }
}
