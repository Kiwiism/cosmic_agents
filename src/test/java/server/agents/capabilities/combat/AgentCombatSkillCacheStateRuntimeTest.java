package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCombatSkillCacheStateRuntimeTest {
    @Test
    void adaptsCombatSkillCacheState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentCombatSkillCacheStateRuntime.matches(entry, 100, 20, 123));

        AgentCombatSkillCacheStateRuntime.reset(entry, 100, 20, 123);
        AgentCombatSkillCacheStateRuntime.addAttackSkillId(entry, 1);
        AgentCombatSkillCacheStateRuntime.setAttackSkillId(entry, 2);
        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, 3, 6);
        AgentCombatSkillCacheStateRuntime.setHealSkillId(entry, 4);
        AgentCombatSkillCacheStateRuntime.addBuffSkillId(entry, 5);
        AgentCombatSkillCacheStateRuntime.addSummonSkillId(entry, 6);

        assertTrue(AgentCombatSkillCacheStateRuntime.matches(entry, 100, 20, 123));
        assertTrue(AgentCombatSkillCacheStateRuntime.hasAttackSkillIds(entry));
        assertEquals(1, AgentCombatSkillCacheStateRuntime.attackSkillIds(entry).get(0));
        assertEquals(2, AgentCombatSkillCacheStateRuntime.attackSkillId(entry));
        assertEquals(3, AgentCombatSkillCacheStateRuntime.aoeSkillId(entry));
        assertEquals(6, AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
        assertTrue(AgentCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry));
        assertEquals(4, AgentCombatSkillCacheStateRuntime.healSkillId(entry));
        assertTrue(AgentCombatSkillCacheStateRuntime.hasBuffSkillIds(entry));
        assertEquals(5, AgentCombatSkillCacheStateRuntime.buffSkillIds(entry).get(0));
        assertEquals(6, AgentCombatSkillCacheStateRuntime.summonSkillIds(entry).get(0));

        AgentCombatSkillCacheStateRuntime.reset(entry, 200, 30, 456);

        assertTrue(AgentCombatSkillCacheStateRuntime.matches(entry, 200, 30, 456));
        assertFalse(AgentCombatSkillCacheStateRuntime.hasAttackSkillIds(entry));
        assertEquals(0, AgentCombatSkillCacheStateRuntime.attackSkillId(entry));
        assertEquals(0, AgentCombatSkillCacheStateRuntime.aoeSkillId(entry));
        assertEquals(1, AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
        assertEquals(0, AgentCombatSkillCacheStateRuntime.healSkillId(entry));
        assertFalse(AgentCombatSkillCacheStateRuntime.hasBuffSkillIds(entry));
        assertTrue(AgentCombatSkillCacheStateRuntime.summonSkillIds(entry).isEmpty());
    }
}
