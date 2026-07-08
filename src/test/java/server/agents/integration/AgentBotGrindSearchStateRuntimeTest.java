package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotGrindSearchStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotGrindSearchStateRuntimeTest {
    @Test
    void adaptsGrindSearchCooldown() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(0L, AgentBotGrindSearchStateRuntime.nextSearchAtMs(entry));
        assertFalse(AgentBotGrindSearchStateRuntime.searchBlocked(entry, 1_000L));

        AgentBotGrindSearchStateRuntime.scheduleNextSearch(entry, 2_000L);

        assertEquals(2_000L, AgentBotGrindSearchStateRuntime.nextSearchAtMs(entry));
        assertTrue(AgentBotGrindSearchStateRuntime.searchBlocked(entry, 1_999L));
        assertFalse(AgentBotGrindSearchStateRuntime.searchBlocked(entry, 2_000L));

        AgentBotGrindSearchStateRuntime.clear(entry);

        assertEquals(0L, AgentBotGrindSearchStateRuntime.nextSearchAtMs(entry));
    }
}
