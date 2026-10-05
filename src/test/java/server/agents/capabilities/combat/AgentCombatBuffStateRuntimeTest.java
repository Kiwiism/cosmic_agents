package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCombatBuffStateRuntimeTest {
    @Test
    void adaptsCombatBuffCooldownAndToggleState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertTrue(AgentCombatBuffStateRuntime.skillBuffsEnabled(entry));
        assertTrue(AgentCombatBuffStateRuntime.supportHealsEnabled(entry));

        AgentCombatBuffStateRuntime.setSkillBuffsEnabled(entry, false);
        AgentCombatBuffStateRuntime.setSupportHealsEnabled(entry, false);

        assertFalse(AgentCombatBuffStateRuntime.skillBuffsEnabled(entry));
        assertFalse(AgentCombatBuffStateRuntime.supportHealsEnabled(entry));

        AgentCombatBuffStateRuntime.ensureNextBuffAt(entry, 100, 1_000L);
        AgentCombatBuffStateRuntime.ensureNextBuffAt(entry, 100, 2_000L);
        AgentCombatBuffStateRuntime.setNextSupportBuffAt(entry, 200, 3_000L);

        assertEquals(1_000L, AgentCombatBuffStateRuntime.nextBuffAt(entry, 100));
        assertEquals(0L, AgentCombatBuffStateRuntime.nextBuffAt(entry, 101));
        assertTrue(AgentCombatBuffStateRuntime.supportBuffOnCooldown(entry, 200, 2_999L));
        assertFalse(AgentCombatBuffStateRuntime.supportBuffOnCooldown(entry, 200, 3_000L));
        assertEquals(3_000L, AgentCombatBuffStateRuntime.nextSupportBuffAt(entry, 200));
    }

    @Test
    void aRebuffRequestMakesEveryBuffDueNow() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentCombatSkillCacheStateRuntime.addBuffSkillId(entry, 2301004);  // Bless
        AgentCombatSkillCacheStateRuntime.addBuffSkillId(entry, 2311003);  // Holy Symbol
        AgentCombatBuffStateRuntime.setNextBuffAt(entry, 2301004, 90_000L);
        AgentCombatBuffStateRuntime.setNextBuffAt(entry, 2311003, 120_000L);
        AgentCombatBuffStateRuntime.setNextSupportBuffAt(entry, 2311003, 60_000L);

        AgentCombatBuffStateRuntime.requestRebuff(entry);

        assertEquals(0L, AgentCombatBuffStateRuntime.nextBuffAt(entry, 2301004));
        assertEquals(0L, AgentCombatBuffStateRuntime.nextBuffAt(entry, 2311003));
        assertFalse(AgentCombatBuffStateRuntime.supportBuffOnCooldown(entry, 2311003, 1L));
    }
}
