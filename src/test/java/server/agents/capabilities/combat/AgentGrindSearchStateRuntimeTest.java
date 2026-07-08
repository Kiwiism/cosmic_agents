package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentGrindSearchStateRuntimeTest {
    @Test
    void adaptsGrindSearchCooldown() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(0L, AgentGrindSearchStateRuntime.nextSearchAtMs(entry));
        assertFalse(AgentGrindSearchStateRuntime.searchBlocked(entry, 1_000L));

        AgentGrindSearchStateRuntime.scheduleNextSearch(entry, 2_000L);

        assertEquals(2_000L, AgentGrindSearchStateRuntime.nextSearchAtMs(entry));
        assertTrue(AgentGrindSearchStateRuntime.searchBlocked(entry, 1_999L));
        assertFalse(AgentGrindSearchStateRuntime.searchBlocked(entry, 2_000L));

        AgentGrindSearchStateRuntime.clear(entry);

        assertEquals(0L, AgentGrindSearchStateRuntime.nextSearchAtMs(entry));
    }
}
