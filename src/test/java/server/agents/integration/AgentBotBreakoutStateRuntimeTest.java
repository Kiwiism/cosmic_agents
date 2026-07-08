package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotBreakoutStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotBreakoutStateRuntimeTest {
    @Test
    void adaptsBreakoutCommitment() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentBotBreakoutStateRuntime.hasBreakoutCommitment(entry));

        AgentBotBreakoutStateRuntime.setBreakoutCommitment(entry, -1, 2_000L);

        assertTrue(AgentBotBreakoutStateRuntime.hasBreakoutCommitment(entry));
        assertEquals(-1, AgentBotBreakoutStateRuntime.direction(entry));
        assertEquals(2_000L, AgentBotBreakoutStateRuntime.untilMs(entry));
        assertFalse(AgentBotBreakoutStateRuntime.isExpired(entry, 1_999L));
        assertTrue(AgentBotBreakoutStateRuntime.isExpired(entry, 2_000L));

        AgentBotBreakoutStateRuntime.clear(entry);

        assertFalse(AgentBotBreakoutStateRuntime.hasBreakoutCommitment(entry));
        assertEquals(0, AgentBotBreakoutStateRuntime.direction(entry));
        assertEquals(0L, AgentBotBreakoutStateRuntime.untilMs(entry));
    }
}
