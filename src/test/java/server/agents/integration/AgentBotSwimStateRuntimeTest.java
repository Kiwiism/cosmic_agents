package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotSwimStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotSwimStateRuntimeTest {
    @Test
    void adaptsSwimIntentState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentBotSwimStateRuntime.swimming(entry));
        assertEquals(0, AgentBotSwimStateRuntime.swimMoveDirection(entry));
        assertEquals(0, AgentBotSwimStateRuntime.swimVerticalHold(entry));
        assertFalse(AgentBotSwimStateRuntime.swimJumpRequested(entry));
        assertEquals(0L, AgentBotSwimStateRuntime.swimNextJumpAtMs(entry));

        AgentBotSwimStateRuntime.setSwimming(entry, true);
        AgentBotSwimStateRuntime.setSwimMoveDirection(entry, -5);
        AgentBotSwimStateRuntime.setSwimVerticalHold(entry, 3);
        AgentBotSwimStateRuntime.setSwimJumpRequested(entry, true);
        AgentBotSwimStateRuntime.setSwimNextJumpAtMs(entry, 123L);

        assertTrue(AgentBotSwimStateRuntime.swimming(entry));
        assertEquals(-1, AgentBotSwimStateRuntime.swimMoveDirection(entry));
        assertEquals(1, AgentBotSwimStateRuntime.swimVerticalHold(entry));
        assertTrue(AgentBotSwimStateRuntime.swimJumpRequested(entry));
        assertEquals(123L, AgentBotSwimStateRuntime.swimNextJumpAtMs(entry));

        AgentBotSwimStateRuntime.clearSwimInput(entry);

        assertEquals(0, AgentBotSwimStateRuntime.swimMoveDirection(entry));
        assertEquals(0, AgentBotSwimStateRuntime.swimVerticalHold(entry));
        assertFalse(AgentBotSwimStateRuntime.swimJumpRequested(entry));
    }
}
