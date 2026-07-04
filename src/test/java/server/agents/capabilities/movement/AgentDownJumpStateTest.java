package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDownJumpStateTest {
    @Test
    void defaultsMatchLegacyBotEntryFields() {
        AgentDownJumpState state = new AgentDownJumpState();

        assertFalse(state.pending());
        assertEquals(0L, state.gracePeriodMs());
        assertFalse(state.hasGracePeriod());
    }

    @Test
    void storesPendingAndGracePeriod() {
        AgentDownJumpState state = new AgentDownJumpState();

        state.setPending(true);
        state.setGracePeriodMs(350L);

        assertTrue(state.pending());
        assertEquals(350L, state.gracePeriodMs());
        assertTrue(state.hasGracePeriod());
    }

    @Test
    void clearResetsPendingAndGracePeriod() {
        AgentDownJumpState state = new AgentDownJumpState();
        state.setPending(true);
        state.setGracePeriodMs(50L);

        state.clear();

        assertFalse(state.pending());
        assertEquals(0L, state.gracePeriodMs());
        assertFalse(state.hasGracePeriod());
    }
}
