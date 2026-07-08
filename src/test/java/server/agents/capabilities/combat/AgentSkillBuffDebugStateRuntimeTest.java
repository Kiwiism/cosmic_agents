package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentSkillBuffDebugStateRuntimeTest {
    @Test
    void adaptsSkillBuffDebugState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals("no skill buff checks yet", AgentSkillBuffDebugStateRuntime.lastActionSummary(entry));
        assertEquals(0L, AgentSkillBuffDebugStateRuntime.lastActionAtMs(entry));
        assertEquals(-1L, AgentSkillBuffDebugStateRuntime.lastActionAgeMs(entry, 1_000L));

        AgentSkillBuffDebugStateRuntime.rememberAction(entry, 900L, "checked buffs");

        assertEquals("checked buffs", AgentSkillBuffDebugStateRuntime.lastActionSummary(entry));
        assertEquals(900L, AgentSkillBuffDebugStateRuntime.lastActionAtMs(entry));
        assertEquals(100L, AgentSkillBuffDebugStateRuntime.lastActionAgeMs(entry, 1_000L));
    }
}
