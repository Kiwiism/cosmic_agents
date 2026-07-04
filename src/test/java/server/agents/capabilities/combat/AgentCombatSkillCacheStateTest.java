package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCombatSkillCacheStateTest {
    @Test
    void storesAndResetsCachedSkillChoices() {
        AgentCombatSkillCacheState state = new AgentCombatSkillCacheState();

        assertFalse(state.matches(100, 20, 123));

        state.reset(100, 20, 123);
        state.addAttackSkillId(1);
        state.setAttackSkillId(2);
        state.setAoeSkill(3, 6);
        state.setHealSkillId(4);
        state.addBuffSkillId(5);
        state.addSummonSkillId(6);

        assertTrue(state.matches(100, 20, 123));
        assertEquals(1, state.attackSkillIds().get(0));
        assertEquals(2, state.attackSkillId());
        assertEquals(3, state.aoeSkillId());
        assertEquals(6, state.aoeSkillMobs());
        assertEquals(4, state.healSkillId());
        assertEquals(5, state.buffSkillIds().get(0));
        assertEquals(6, state.summonSkillIds().get(0));

        state.reset(200, 30, 456);

        assertTrue(state.matches(200, 30, 456));
        assertTrue(state.attackSkillIds().isEmpty());
        assertEquals(0, state.attackSkillId());
        assertEquals(0, state.aoeSkillId());
        assertEquals(1, state.aoeSkillMobs());
        assertEquals(0, state.healSkillId());
        assertTrue(state.buffSkillIds().isEmpty());
        assertTrue(state.summonSkillIds().isEmpty());
    }
}
