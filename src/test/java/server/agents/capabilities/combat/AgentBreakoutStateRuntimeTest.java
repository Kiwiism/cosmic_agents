package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBreakoutStateRuntimeTest {
    @Test
    void adaptsBreakoutCommitment() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentBreakoutStateRuntime.hasBreakoutCommitment(entry));

        AgentBreakoutStateRuntime.setBreakoutCommitment(entry, -1, 2_000L);

        assertTrue(AgentBreakoutStateRuntime.hasBreakoutCommitment(entry));
        assertEquals(-1, AgentBreakoutStateRuntime.direction(entry));
        assertEquals(2_000L, AgentBreakoutStateRuntime.untilMs(entry));
        assertFalse(AgentBreakoutStateRuntime.isExpired(entry, 1_999L));
        assertTrue(AgentBreakoutStateRuntime.isExpired(entry, 2_000L));

        AgentBreakoutStateRuntime.clear(entry);

        assertFalse(AgentBreakoutStateRuntime.hasBreakoutCommitment(entry));
        assertEquals(0, AgentBreakoutStateRuntime.direction(entry));
        assertEquals(0L, AgentBreakoutStateRuntime.untilMs(entry));
    }
}
