package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementTargetSnapshot;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void adaptsLastDecisionAndBlockReasonState() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentBotNavigationDebugStateRuntime.setLastDecision(entry, "graph-warmup");
        assertTrue("graph-warmup".equals(AgentBotNavigationDebugStateRuntime.lastDecision(entry)));

        AgentBotNavigationDebugStateRuntime.setLastEdgeBlockReason(entry, "climb-pos");
        assertTrue("climb-pos".equals(AgentBotNavigationDebugStateRuntime.lastEdgeBlockReason(entry)));
        assertTrue("graph-warmup[climb-pos]".equals(
                AgentBotNavigationDebugStateRuntime.decisionWithBlockReason(entry)));

        AgentBotNavigationDebugStateRuntime.clearLastEdgeBlockReason(entry);
        assertTrue("graph-warmup".equals(AgentBotNavigationDebugStateRuntime.decisionWithBlockReason(entry)));
    }

    @Test
    void adaptsGraphWarmupFallbackState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotNavigationDebugStateRuntime.graphWarmupFallback(entry));

        AgentBotNavigationDebugStateRuntime.setGraphWarmupFallback(entry, true);

        assertTrue(AgentBotNavigationDebugStateRuntime.graphWarmupFallback(entry));

        AgentBotNavigationDebugStateRuntime.clearGraphWarmupFallback(entry);

        assertFalse(AgentBotNavigationDebugStateRuntime.graphWarmupFallback(entry));
    }

    @Test
    void adaptsNavTargetStateWithPointCopies() {
        BotEntry entry = new BotEntry(null, null, null);
        Point waypoint = new Point(20, 30);

        AgentBotNavigationDebugStateRuntime.setNavTargetRegionId(entry, 7);
        AgentBotNavigationDebugStateRuntime.setNavWaypoint(entry, waypoint, true);

        waypoint.x = 99;
        Point exposed = AgentBotNavigationDebugStateRuntime.navTargetPosition(entry);
        exposed.y = 88;

        assertTrue(AgentBotNavigationDebugStateRuntime.hasNavTargetPosition(entry));
        assertEquals(new Point(20, 30), AgentBotNavigationDebugStateRuntime.navTargetPosition(entry));
        assertEquals(7, AgentBotNavigationDebugStateRuntime.navTargetRegionId(entry));
        assertTrue(AgentBotNavigationDebugStateRuntime.navPreciseTarget(entry));

        AgentBotNavigationDebugStateRuntime.clearNavTarget(entry);

        assertFalse(AgentBotNavigationDebugStateRuntime.hasNavTargetPosition(entry));
        assertNull(AgentBotNavigationDebugStateRuntime.navTargetPosition(entry));
        assertEquals(-1, AgentBotNavigationDebugStateRuntime.navTargetRegionId(entry));
        assertFalse(AgentBotNavigationDebugStateRuntime.navPreciseTarget(entry));
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
