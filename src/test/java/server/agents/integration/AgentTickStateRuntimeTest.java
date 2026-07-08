package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentTickStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTickStateRuntimeTest {
    @Test
    void adaptsTickHeartbeatAndFollowIdleTimers() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentTickStateRuntime.lastTickWasAi(entry));
        assertEquals(0L, AgentTickStateRuntime.lastTickAtMs(entry));
        assertTrue(AgentTickStateRuntime.heartbeatDue(entry, 600_000L, 600_000L));

        AgentTickStateRuntime.recordTick(entry, true, 1_234L);
        AgentTickStateRuntime.markHeartbeat(entry, 600_000L);
        AgentTickStateRuntime.setNextFollowIdleMovementCheckAtMs(entry, 2_000L);

        assertTrue(AgentTickStateRuntime.lastTickWasAi(entry));
        assertEquals(1_234L, AgentTickStateRuntime.lastTickAtMs(entry));
        assertFalse(AgentTickStateRuntime.heartbeatDue(entry, 600_001L, 600_000L));
        assertTrue(AgentTickStateRuntime.heartbeatDue(entry, 1_200_000L, 600_000L));
        assertEquals(2_000L, AgentTickStateRuntime.nextFollowIdleMovementCheckAtMs(entry));
    }
}
