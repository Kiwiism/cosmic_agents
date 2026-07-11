package server.agents.capabilities.combat;

import constants.skills.Crusader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentComboFinisherPolicyTest {
    @Test
    void finisherRequiresAtLeastOneOrb() {
        assertFalse(AgentComboFinisherPolicy.canPlan(Crusader.SWORD_PANIC, null));
        assertFalse(AgentComboFinisherPolicy.canPlan(Crusader.SWORD_PANIC, 1));
        assertTrue(AgentComboFinisherPolicy.canPlan(Crusader.SWORD_PANIC, 2));
    }

    @Test
    void ordinarySkillsDoNotRequireComboBuff() {
        assertTrue(AgentComboFinisherPolicy.canPlan(1001005, null));
    }
}
