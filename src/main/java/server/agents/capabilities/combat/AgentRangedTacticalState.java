package server.agents.capabilities.combat;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Capability-owned commitments used while positioning ranged attackers. */
public final class AgentRangedTacticalState {
    public static final AgentCapabilityStateKey<AgentRangedTacticalState> STATE_KEY =
            new AgentCapabilityStateKey<>("combat.ranged-tactics",
                    AgentRangedTacticalState.class, AgentRangedTacticalState::new);

    private final AgentDegenerateAttackState degenerateAttack = new AgentDegenerateAttackState();
    private final AgentRetreatHoldState retreatHold = new AgentRetreatHoldState();
    private final AgentBreakoutState breakout = new AgentBreakoutState();
    private final AgentAoeRepositionState aoeReposition = new AgentAoeRepositionState();

    AgentDegenerateAttackState degenerateAttack() {
        return degenerateAttack;
    }

    AgentRetreatHoldState retreatHold() {
        return retreatHold;
    }

    AgentBreakoutState breakout() {
        return breakout;
    }

    AgentAoeRepositionState aoeReposition() {
        return aoeReposition;
    }
}
