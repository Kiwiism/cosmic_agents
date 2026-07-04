package server.agents.runtime;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLeaderActivityStateTest {
    @Test
    void storesAfkActivityState() {
        AgentLeaderActivityState state = new AgentLeaderActivityState();
        Point position = new Point(12, 34);

        assertNull(state.afkPosition());
        assertEquals(0L, state.afkSinceMs());
        assertFalse(state.wasAfk());

        state.setAfkPosition(position);
        state.setAfkSinceMs(1_234L);
        state.setWasAfk(true);

        assertSame(position, state.afkPosition());
        assertEquals(1_234L, state.afkSinceMs());
        assertTrue(state.wasAfk());
    }

    @Test
    void storesInactiveSafetyState() {
        AgentLeaderActivityState state = new AgentLeaderActivityState();

        assertEquals(0L, state.offlineOrDeadSinceMs());
        assertFalse(state.returnedToTown());
        assertFalse(state.awaySafeMode());

        state.setOfflineOrDeadSinceMs(5_000L);
        state.setReturnedToTown(true);
        state.setAwaySafeMode(true);

        assertEquals(5_000L, state.offlineOrDeadSinceMs());
        assertTrue(state.returnedToTown());
        assertTrue(state.awaySafeMode());
    }

    @Test
    void recordsLastCommandMetadata() {
        AgentLeaderActivityState state = new AgentLeaderActivityState();

        assertNull(state.lastCommand());
        assertEquals(0L, state.lastCommandAtMs());

        state.recordLastCommand("follow me", 9_000L);

        assertEquals("follow me", state.lastCommand());
        assertEquals(9_000L, state.lastCommandAtMs());
    }
}
