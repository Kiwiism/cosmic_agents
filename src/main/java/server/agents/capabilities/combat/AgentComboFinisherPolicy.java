package server.agents.capabilities.combat;

import constants.game.GameConstants;

/** Client-equivalent combo-orb requirement for Panic/Coma attack planning. */
public final class AgentComboFinisherPolicy {
    private AgentComboFinisherPolicy() {
    }

    public static boolean canPlan(int skillId, Integer comboBuffValue) {
        return !GameConstants.isFinisherSkill(skillId)
                || comboBuffValue != null && comboBuffValue >= 2;
    }
}
