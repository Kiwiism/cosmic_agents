package server.agents.capabilities.combat;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Temporary activity-owned restriction; zero means ordinary combat policy. */
public final class AgentCombatSkillConstraintState {
    public static final AgentCapabilityStateKey<AgentCombatSkillConstraintState> STATE_KEY =
            new AgentCapabilityStateKey<>("combat.skill-constraint",
                    AgentCombatSkillConstraintState.class, AgentCombatSkillConstraintState::new);

    private int requiredSkillId;

    public synchronized int requiredSkillId() { return requiredSkillId; }
    public synchronized void require(int skillId) { requiredSkillId = Math.max(0, skillId); }
    public synchronized void clear() { requiredSkillId = 0; }
}
