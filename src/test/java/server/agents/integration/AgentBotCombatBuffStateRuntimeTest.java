package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotCombatBuffStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotCombatBuffStateRuntimeTest {
    @Test
    void adaptsCombatBuffCooldownAndToggleState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertTrue(AgentBotCombatBuffStateRuntime.skillBuffsEnabled(entry));
        assertTrue(AgentBotCombatBuffStateRuntime.supportHealsEnabled(entry));

        AgentBotCombatBuffStateRuntime.setSkillBuffsEnabled(entry, false);
        AgentBotCombatBuffStateRuntime.setSupportHealsEnabled(entry, false);

        assertFalse(AgentBotCombatBuffStateRuntime.skillBuffsEnabled(entry));
        assertFalse(AgentBotCombatBuffStateRuntime.supportHealsEnabled(entry));

        AgentBotCombatBuffStateRuntime.ensureNextBuffAt(entry, 100, 1_000L);
        AgentBotCombatBuffStateRuntime.ensureNextBuffAt(entry, 100, 2_000L);
        AgentBotCombatBuffStateRuntime.setNextSupportBuffAt(entry, 200, 3_000L);

        assertEquals(1_000L, AgentBotCombatBuffStateRuntime.nextBuffAt(entry, 100));
        assertEquals(0L, AgentBotCombatBuffStateRuntime.nextBuffAt(entry, 101));
        assertTrue(AgentBotCombatBuffStateRuntime.supportBuffOnCooldown(entry, 200, 2_999L));
        assertFalse(AgentBotCombatBuffStateRuntime.supportBuffOnCooldown(entry, 200, 3_000L));
        assertEquals(3_000L, AgentBotCombatBuffStateRuntime.nextSupportBuffAt(entry, 200));
    }
}
