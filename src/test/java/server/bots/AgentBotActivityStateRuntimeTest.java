package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotActivityStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotActivityStateRuntimeTest {
    @Test
    void adaptsAfkState() {
        BotEntry entry = new BotEntry(null, null, null);
        Point position = new Point(12, 34);

        AgentBotActivityStateRuntime.setOwnerAfkPosition(entry, position);
        AgentBotActivityStateRuntime.setOwnerAfkSinceMs(entry, 1_234L);
        AgentBotActivityStateRuntime.setOwnerWasAfk(entry, true);

        assertEquals(position, AgentBotActivityStateRuntime.ownerAfkPosition(entry));
        assertEquals(1_234L, AgentBotActivityStateRuntime.ownerAfkSinceMs(entry));
        assertTrue(AgentBotActivityStateRuntime.ownerWasAfk(entry));
    }

    @Test
    void adaptsOwnerInactiveSafeModeState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotActivityStateRuntime.ownerInactiveTimerStarted(entry));
        assertFalse(AgentBotActivityStateRuntime.ownerReturnedToTown(entry));
        assertFalse(AgentBotActivityStateRuntime.ownerAwaySafeMode(entry));

        AgentBotActivityStateRuntime.startOwnerInactiveTimer(entry, 5_000L);
        AgentBotActivityStateRuntime.setOwnerReturnedToTown(entry, true);
        AgentBotActivityStateRuntime.setOwnerAwaySafeMode(entry, true);

        assertTrue(AgentBotActivityStateRuntime.ownerInactiveTimerStarted(entry));
        assertEquals(5_000L, AgentBotActivityStateRuntime.ownerOfflineOrDeadSinceMs(entry));
        assertTrue(AgentBotActivityStateRuntime.ownerReturnedToTown(entry));
        assertTrue(AgentBotActivityStateRuntime.ownerAwaySafeMode(entry));

        AgentBotActivityStateRuntime.clearOwnerInactiveState(entry);

        assertFalse(AgentBotActivityStateRuntime.ownerInactiveTimerStarted(entry));
        assertEquals(0L, AgentBotActivityStateRuntime.ownerOfflineOrDeadSinceMs(entry));
        assertFalse(AgentBotActivityStateRuntime.ownerReturnedToTown(entry));
        assertFalse(AgentBotActivityStateRuntime.ownerAwaySafeMode(entry));
    }

    @Test
    void adaptsLastOwnerCommandState() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentBotActivityStateRuntime.recordLastOwnerCommand(entry, "follow me", 9_000L);

        assertEquals("follow me", AgentBotActivityStateRuntime.lastOwnerCommand(entry));
        assertEquals(9_000L, AgentBotActivityStateRuntime.lastOwnerCommandAtMs(entry));
    }
}
