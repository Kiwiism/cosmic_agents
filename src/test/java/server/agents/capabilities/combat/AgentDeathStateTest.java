package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDeathStateTest {
    @Test
    void entersAndClearsDeathWindow() {
        AgentDeathState state = new AgentDeathState();

        state.enterDeadState(1_000L, 5_000L);

        assertEquals(1_000L, state.deadSinceMs());
        assertEquals(6_000L, state.deadUntilMs());
        assertTrue(state.isDead());

        state.clear();

        assertEquals(0L, state.deadSinceMs());
        assertEquals(0L, state.deadUntilMs());
        assertFalse(state.isDead());
    }

    @Test
    void detectsEntryIntoDeadStateOnlyOnce() {
        AgentDeathState state = new AgentDeathState();

        assertTrue(state.shouldEnterDeadState(0));
        state.enterDeadState(1_000L, 5_000L);
        assertFalse(state.shouldEnterDeadState(0));
        assertFalse(state.shouldEnterDeadState(1));
    }

    @Test
    void detectsRespawnDueAfterDeadline() {
        AgentDeathState state = new AgentDeathState();
        state.enterDeadState(1_000L, 5_000L);

        assertFalse(state.isRespawnDue(5_999L));
        assertTrue(state.isRespawnDue(6_000L));
        assertTrue(state.isRespawnDue(6_001L));

        state.clear();
        assertFalse(state.isRespawnDue(10_000L));
    }
}
