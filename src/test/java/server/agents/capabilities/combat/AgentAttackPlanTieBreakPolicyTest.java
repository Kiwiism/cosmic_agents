package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAttackPlanTieBreakPolicyTest {
    @Test
    void shouldPreferShorterCooldown() {
        assertTrue(AgentAttackPlanTieBreakPolicy.isBetter(600, 2001005, 900, 0));
        assertFalse(AgentAttackPlanTieBreakPolicy.isBetter(900, 0, 600, 2001005));
    }

    @Test
    void shouldPreferLowerSkillIdWhenCooldownMatches() {
        assertTrue(AgentAttackPlanTieBreakPolicy.isBetter(720, 1001005, 720, 2001005));
        assertFalse(AgentAttackPlanTieBreakPolicy.isBetter(720, 2001005, 720, 1001005));
    }
}
