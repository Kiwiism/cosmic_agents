package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementTargetSnapshot;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentBotNavigationDebugStateRuntimeTest {
    @Test
    void startsAndClearsPathLoggerState() {
        Character bot = mock(Character.class);
        when(bot.getName()).thenReturn("agent123");
        when(bot.getMapId()).thenReturn(100000000);
        when(bot.getPosition()).thenReturn(new Point(10, 20));
        BotEntry entry = new BotEntry(bot, null, null);

        assertFalse(AgentBotNavigationDebugStateRuntime.isPathLogging(entry));

        AgentBotNavigationDebugStateRuntime.startPathLogging(entry);

        assertTrue(AgentBotNavigationDebugStateRuntime.isPathLogging(entry));
        assertNotNull(entry.pathLogger());

        AgentBotNavigationDebugStateRuntime.clearPathLogging(entry);

        assertFalse(AgentBotNavigationDebugStateRuntime.isPathLogging(entry));
    }

    @Test
    void recordPathLogNoopsWithoutActiveLogger() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentMovementTargetSnapshot snapshot = snapshot();

        AgentBotNavigationDebugStateRuntime.recordPathLog(entry, snapshot, -1, false, false);

        assertFalse(AgentBotNavigationDebugStateRuntime.isPathLogging(entry));
    }

    private static AgentMovementTargetSnapshot snapshot() {
        return new AgentMovementTargetSnapshot(
                "line",
                60,
                30,
                new Point(0, 0),
                new Point(0, 0),
                "owner",
                new Point(0, 0),
                new Point(0, 0),
                null,
                null,
                null,
                new Point(0, 0),
                "owner",
                new Point(0, 0),
                "owner");
    }
}
