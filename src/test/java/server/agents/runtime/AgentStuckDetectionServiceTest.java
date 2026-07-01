package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotMovementStuckStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentStuckDetectionServiceTest {
    @Test
    void resetsProgressWhenNotActivelyNavigating() {
        BotEntry entry = entryAt(new Point(10, 20));
        AgentBotMovementStuckStateRuntime.addStuckMs(entry, 250);
        AgentBotMovementStuckStateRuntime.rememberStuckCheckPosition(entry, new Point(10, 20));
        AtomicInteger unstucks = new AtomicInteger();

        AgentStuckDetectionService.tickStuckDetection(entry, hooks(unstucks, 50, true, remaining -> remaining));

        assertEquals(0, AgentBotMovementStuckStateRuntime.stuckMs(entry));
        assertFalse(AgentBotMovementStuckStateRuntime.hasStuckCheckPosition(entry));
        assertEquals(0, unstucks.get());
    }

    @Test
    void firstActiveNavigationTickRemembersPosition() {
        BotEntry entry = entryAt(new Point(10, 20));
        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, new Point(100, 20), false);
        AtomicInteger unstucks = new AtomicInteger();

        AgentStuckDetectionService.tickStuckDetection(entry, hooks(unstucks, 50, true, remaining -> remaining));

        assertTrue(AgentBotMovementStuckStateRuntime.hasStuckCheckPosition(entry));
        assertEquals(0, AgentBotMovementStuckStateRuntime.stuckMs(entry));
        assertEquals(0, unstucks.get());
    }

    @Test
    void movingResetsStuckProgressAndUpdatesCheckPosition() {
        Character agent = agentAt(new Point(10, 20));
        BotEntry entry = new BotEntry(agent, mock(Character.class), null);
        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, new Point(100, 20), false);
        AgentBotMovementStuckStateRuntime.rememberStuckCheckPosition(entry, new Point(0, 20));
        AgentBotMovementStuckStateRuntime.addStuckMs(entry, 250);
        AtomicInteger unstucks = new AtomicInteger();

        AgentStuckDetectionService.tickStuckDetection(entry, hooks(unstucks, 50, true, remaining -> remaining));

        assertEquals(0, AgentBotMovementStuckStateRuntime.stuckMs(entry));
        assertTrue(AgentBotMovementStuckStateRuntime.hasStuckCheckPosition(entry));
        assertEquals(0, unstucks.get());
    }

    @Test
    void stationaryNavigationTriggersUnstuckWhenEnabledAndPastThreshold() {
        BotEntry entry = entryAt(new Point(10, 20));
        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, new Point(100, 20), false);
        AgentBotMovementStuckStateRuntime.rememberStuckCheckPosition(entry, new Point(10, 20));
        AtomicInteger unstucks = new AtomicInteger();

        AgentStuckDetectionService.tickStuckDetection(entry, hooks(unstucks, 500, true, remaining -> remaining));

        assertEquals(0, AgentBotMovementStuckStateRuntime.stuckMs(entry));
        assertFalse(AgentBotMovementStuckStateRuntime.hasStuckCheckPosition(entry));
        assertEquals(1, unstucks.get());
    }

    @Test
    void cooldownBlocksUnstuckTrigger() {
        BotEntry entry = entryAt(new Point(10, 20));
        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, new Point(100, 20), false);
        AgentBotMovementStuckStateRuntime.rememberStuckCheckPosition(entry, new Point(10, 20));
        AgentBotMovementStuckStateRuntime.setUnstuckCooldownMs(entry, 100);
        AtomicInteger unstucks = new AtomicInteger();

        AgentStuckDetectionService.tickStuckDetection(entry, hooks(unstucks, 500, true, remaining -> remaining));

        assertEquals(500, AgentBotMovementStuckStateRuntime.stuckMs(entry));
        assertEquals(0, unstucks.get());
    }

    @Test
    void inAirResetsProgressDuringNavigation() {
        BotEntry entry = entryAt(new Point(10, 20));
        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, new Point(100, 20), false);
        AgentBotMovementStateRuntime.setInAir(entry, true);
        AgentBotMovementStuckStateRuntime.addStuckMs(entry, 250);
        AgentBotMovementStuckStateRuntime.rememberStuckCheckPosition(entry, new Point(10, 20));
        AtomicInteger unstucks = new AtomicInteger();

        AgentStuckDetectionService.tickStuckDetection(entry, hooks(unstucks, 50, true, remaining -> remaining));

        assertEquals(0, AgentBotMovementStuckStateRuntime.stuckMs(entry));
        assertFalse(AgentBotMovementStuckStateRuntime.hasStuckCheckPosition(entry));
        assertEquals(0, unstucks.get());
    }

    private static AgentStuckDetectionService.StuckDetectionHooks hooks(AtomicInteger unstucks,
                                                                       int movementTickMs,
                                                                       boolean enableUnstuck,
                                                                       java.util.function.IntUnaryOperator tickDown) {
        return new AgentStuckDetectionService.StuckDetectionHooks(
                tickDown,
                entry -> unstucks.incrementAndGet(),
                movementTickMs,
                enableUnstuck);
    }

    private static BotEntry entryAt(Point position) {
        return new BotEntry(agentAt(position), mock(Character.class), null);
    }

    private static Character agentAt(Point position) {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(position));
        return agent;
    }
}
