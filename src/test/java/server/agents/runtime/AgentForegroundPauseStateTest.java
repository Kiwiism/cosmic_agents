package server.agents.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentForegroundPauseStateTest {
    @Test
    void overlappingReasonsPauseOneLogicalClockWindow() {
        AgentForegroundPauseState state = new AgentForegroundPauseState();
        state.pause("crowd", 1_000L);
        state.pause("maintenance", 1_500L);

        assertTrue(state.paused());
        assertEquals(1_000L, state.effectiveNow(2_000L));
        state.resume("crowd", 2_500L);
        assertTrue(state.paused());
        state.resume("maintenance", 3_000L);

        assertFalse(state.paused());
        assertEquals(2_000L, state.effectiveNow(4_000L));
    }
}
