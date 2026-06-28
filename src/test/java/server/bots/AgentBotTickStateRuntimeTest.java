package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotTickStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotTickStateRuntimeTest {
    @Test
    void adaptsTickHeartbeatAndFollowIdleTimers() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotTickStateRuntime.lastTickWasAi(entry));
        assertEquals(0L, AgentBotTickStateRuntime.lastTickAtMs(entry));
        assertTrue(AgentBotTickStateRuntime.heartbeatDue(entry, 600_000L, 600_000L));

        AgentBotTickStateRuntime.recordTick(entry, true, 1_234L);
        AgentBotTickStateRuntime.markHeartbeat(entry, 600_000L);
        AgentBotTickStateRuntime.setNextFollowIdleMovementCheckAtMs(entry, 2_000L);

        assertTrue(AgentBotTickStateRuntime.lastTickWasAi(entry));
        assertEquals(1_234L, AgentBotTickStateRuntime.lastTickAtMs(entry));
        assertFalse(AgentBotTickStateRuntime.heartbeatDue(entry, 600_001L, 600_000L));
        assertTrue(AgentBotTickStateRuntime.heartbeatDue(entry, 1_200_000L, 600_000L));
        assertEquals(2_000L, AgentBotTickStateRuntime.nextFollowIdleMovementCheckAtMs(entry));
    }
}
