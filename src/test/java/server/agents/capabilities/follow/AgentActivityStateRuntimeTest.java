package server.agents.capabilities.follow;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentActivityStateRuntimeTest {
    @Test
    void adaptsAfkState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Point position = new Point(12, 34);

        AgentActivityStateRuntime.setOwnerAfkPosition(entry, position);
        AgentActivityStateRuntime.setOwnerAfkSinceMs(entry, 1_234L);
        AgentActivityStateRuntime.setOwnerWasAfk(entry, true);

        assertEquals(position, AgentActivityStateRuntime.ownerAfkPosition(entry));
        assertEquals(1_234L, AgentActivityStateRuntime.ownerAfkSinceMs(entry));
        assertTrue(AgentActivityStateRuntime.ownerWasAfk(entry));
    }

    @Test
    void adaptsOwnerInactiveSafeModeState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentActivityStateRuntime.ownerInactiveTimerStarted(entry));
        assertFalse(AgentActivityStateRuntime.ownerReturnedToTown(entry));
        assertFalse(AgentActivityStateRuntime.ownerAwaySafeMode(entry));

        AgentActivityStateRuntime.startOwnerInactiveTimer(entry, 5_000L);
        AgentActivityStateRuntime.setOwnerReturnedToTown(entry, true);
        AgentActivityStateRuntime.setOwnerAwaySafeMode(entry, true);

        assertTrue(AgentActivityStateRuntime.ownerInactiveTimerStarted(entry));
        assertEquals(5_000L, AgentActivityStateRuntime.ownerOfflineOrDeadSinceMs(entry));
        assertTrue(AgentActivityStateRuntime.ownerReturnedToTown(entry));
        assertTrue(AgentActivityStateRuntime.ownerAwaySafeMode(entry));

        AgentActivityStateRuntime.clearOwnerInactiveState(entry);

        assertFalse(AgentActivityStateRuntime.ownerInactiveTimerStarted(entry));
        assertEquals(0L, AgentActivityStateRuntime.ownerOfflineOrDeadSinceMs(entry));
        assertFalse(AgentActivityStateRuntime.ownerReturnedToTown(entry));
        assertFalse(AgentActivityStateRuntime.ownerAwaySafeMode(entry));
    }

    @Test
    void adaptsLastOwnerCommandState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentActivityStateRuntime.recordLastOwnerCommand(entry, "follow me", 9_000L);

        assertEquals("follow me", AgentActivityStateRuntime.lastOwnerCommand(entry));
        assertEquals(9_000L, AgentActivityStateRuntime.lastOwnerCommandAtMs(entry));
    }
}
