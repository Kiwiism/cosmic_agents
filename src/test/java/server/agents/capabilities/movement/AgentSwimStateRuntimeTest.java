package server.agents.capabilities.movement;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSwimStateRuntimeTest {
    @Test
    void adaptsSwimIntentState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentSwimStateRuntime.swimming(entry));
        assertEquals(0, AgentSwimStateRuntime.swimMoveDirection(entry));
        assertEquals(0, AgentSwimStateRuntime.swimVerticalHold(entry));
        assertFalse(AgentSwimStateRuntime.swimJumpRequested(entry));
        assertEquals(0L, AgentSwimStateRuntime.swimNextJumpAtMs(entry));

        AgentSwimStateRuntime.setSwimming(entry, true);
        AgentSwimStateRuntime.setSwimMoveDirection(entry, -5);
        AgentSwimStateRuntime.setSwimVerticalHold(entry, 3);
        AgentSwimStateRuntime.setSwimJumpRequested(entry, true);
        AgentSwimStateRuntime.setSwimNextJumpAtMs(entry, 123L);

        assertTrue(AgentSwimStateRuntime.swimming(entry));
        assertEquals(-1, AgentSwimStateRuntime.swimMoveDirection(entry));
        assertEquals(1, AgentSwimStateRuntime.swimVerticalHold(entry));
        assertTrue(AgentSwimStateRuntime.swimJumpRequested(entry));
        assertEquals(123L, AgentSwimStateRuntime.swimNextJumpAtMs(entry));

        AgentSwimStateRuntime.clearSwimInput(entry);

        assertEquals(0, AgentSwimStateRuntime.swimMoveDirection(entry));
        assertEquals(0, AgentSwimStateRuntime.swimVerticalHold(entry));
        assertFalse(AgentSwimStateRuntime.swimJumpRequested(entry));
    }
}
