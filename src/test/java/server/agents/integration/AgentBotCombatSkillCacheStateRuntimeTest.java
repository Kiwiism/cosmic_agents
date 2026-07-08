package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotCombatSkillCacheStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotCombatSkillCacheStateRuntimeTest {
    @Test
    void adaptsCombatSkillCacheState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentBotCombatSkillCacheStateRuntime.matches(entry, 100, 20, 123));

        AgentBotCombatSkillCacheStateRuntime.reset(entry, 100, 20, 123);
        AgentBotCombatSkillCacheStateRuntime.addAttackSkillId(entry, 1);
        AgentBotCombatSkillCacheStateRuntime.setAttackSkillId(entry, 2);
        AgentBotCombatSkillCacheStateRuntime.setAoeSkill(entry, 3, 6);
        AgentBotCombatSkillCacheStateRuntime.setHealSkillId(entry, 4);
        AgentBotCombatSkillCacheStateRuntime.addBuffSkillId(entry, 5);
        AgentBotCombatSkillCacheStateRuntime.addSummonSkillId(entry, 6);

        assertTrue(AgentBotCombatSkillCacheStateRuntime.matches(entry, 100, 20, 123));
        assertTrue(AgentBotCombatSkillCacheStateRuntime.hasAttackSkillIds(entry));
        assertEquals(1, AgentBotCombatSkillCacheStateRuntime.attackSkillIds(entry).get(0));
        assertEquals(2, AgentBotCombatSkillCacheStateRuntime.attackSkillId(entry));
        assertEquals(3, AgentBotCombatSkillCacheStateRuntime.aoeSkillId(entry));
        assertEquals(6, AgentBotCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
        assertTrue(AgentBotCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry));
        assertEquals(4, AgentBotCombatSkillCacheStateRuntime.healSkillId(entry));
        assertTrue(AgentBotCombatSkillCacheStateRuntime.hasBuffSkillIds(entry));
        assertEquals(5, AgentBotCombatSkillCacheStateRuntime.buffSkillIds(entry).get(0));
        assertEquals(6, AgentBotCombatSkillCacheStateRuntime.summonSkillIds(entry).get(0));

        AgentBotCombatSkillCacheStateRuntime.reset(entry, 200, 30, 456);

        assertTrue(AgentBotCombatSkillCacheStateRuntime.matches(entry, 200, 30, 456));
        assertFalse(AgentBotCombatSkillCacheStateRuntime.hasAttackSkillIds(entry));
        assertEquals(0, AgentBotCombatSkillCacheStateRuntime.attackSkillId(entry));
        assertEquals(0, AgentBotCombatSkillCacheStateRuntime.aoeSkillId(entry));
        assertEquals(1, AgentBotCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
        assertEquals(0, AgentBotCombatSkillCacheStateRuntime.healSkillId(entry));
        assertFalse(AgentBotCombatSkillCacheStateRuntime.hasBuffSkillIds(entry));
        assertTrue(AgentBotCombatSkillCacheStateRuntime.summonSkillIds(entry).isEmpty());
    }
}
