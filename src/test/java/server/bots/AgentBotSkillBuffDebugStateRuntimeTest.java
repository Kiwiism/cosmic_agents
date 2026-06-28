package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotSkillBuffDebugStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentBotSkillBuffDebugStateRuntimeTest {
    @Test
    void adaptsSkillBuffDebugState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertEquals("no skill buff checks yet", AgentBotSkillBuffDebugStateRuntime.lastActionSummary(entry));
        assertEquals(0L, AgentBotSkillBuffDebugStateRuntime.lastActionAtMs(entry));
        assertEquals(-1L, AgentBotSkillBuffDebugStateRuntime.lastActionAgeMs(entry, 1_000L));

        AgentBotSkillBuffDebugStateRuntime.rememberAction(entry, 900L, "checked buffs");

        assertEquals("checked buffs", AgentBotSkillBuffDebugStateRuntime.lastActionSummary(entry));
        assertEquals(900L, AgentBotSkillBuffDebugStateRuntime.lastActionAtMs(entry));
        assertEquals(100L, AgentBotSkillBuffDebugStateRuntime.lastActionAgeMs(entry, 1_000L));
    }
}
