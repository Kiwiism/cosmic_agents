package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;
import server.agents.integration.AgentMovementStateRuntime;
import server.agents.integration.AgentMovementStuckStateRuntime;

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
        AgentRuntimeEntry entry = entryAt(new Point(10, 20));
        AgentMovementStuckStateRuntime.addStuckMs(entry, 250);
        AgentMovementStuckStateRuntime.rememberStuckCheckPosition(entry, new Point(10, 20));
        AtomicInteger unstucks = new AtomicInteger();

        AgentStuckDetectionService.tickStuckDetection(entry, hooks(unstucks, 50, true, remaining -> remaining));

        assertEquals(0, AgentMovementStuckStateRuntime.stuckMs(entry));
        assertFalse(AgentMovementStuckStateRuntime.hasStuckCheckPosition(entry));
        assertEquals(0, unstucks.get());
    }

    @Test
    void firstActiveNavigationTickRemembersPosition() {
        AgentRuntimeEntry entry = entryAt(new Point(10, 20));
        AgentMoveTargetStateRuntime.setMoveTarget(entry, new Point(100, 20), false);
        AtomicInteger unstucks = new AtomicInteger();

        AgentStuckDetectionService.tickStuckDetection(entry, hooks(unstucks, 50, true, remaining -> remaining));

        assertTrue(AgentMovementStuckStateRuntime.hasStuckCheckPosition(entry));
        assertEquals(0, AgentMovementStuckStateRuntime.stuckMs(entry));
        assertEquals(0, unstucks.get());
    }

    @Test
    void movingResetsStuckProgressAndUpdatesCheckPosition() {
        Character agent = agentAt(new Point(10, 20));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        AgentMoveTargetStateRuntime.setMoveTarget(entry, new Point(100, 20), false);
        AgentMovementStuckStateRuntime.rememberStuckCheckPosition(entry, new Point(0, 20));
        AgentMovementStuckStateRuntime.addStuckMs(entry, 250);
        AtomicInteger unstucks = new AtomicInteger();

        AgentStuckDetectionService.tickStuckDetection(entry, hooks(unstucks, 50, true, remaining -> remaining));

        assertEquals(0, AgentMovementStuckStateRuntime.stuckMs(entry));
        assertTrue(AgentMovementStuckStateRuntime.hasStuckCheckPosition(entry));
        assertEquals(0, unstucks.get());
    }

    @Test
    void stationaryNavigationTriggersUnstuckWhenEnabledAndPastThreshold() {
        AgentRuntimeEntry entry = entryAt(new Point(10, 20));
        AgentMoveTargetStateRuntime.setMoveTarget(entry, new Point(100, 20), false);
        AgentMovementStuckStateRuntime.rememberStuckCheckPosition(entry, new Point(10, 20));
        AtomicInteger unstucks = new AtomicInteger();

        AgentStuckDetectionService.tickStuckDetection(entry, hooks(unstucks, 500, true, remaining -> remaining));

        assertEquals(0, AgentMovementStuckStateRuntime.stuckMs(entry));
        assertFalse(AgentMovementStuckStateRuntime.hasStuckCheckPosition(entry));
        assertEquals(1, unstucks.get());
    }

    @Test
    void cooldownBlocksUnstuckTrigger() {
        AgentRuntimeEntry entry = entryAt(new Point(10, 20));
        AgentMoveTargetStateRuntime.setMoveTarget(entry, new Point(100, 20), false);
        AgentMovementStuckStateRuntime.rememberStuckCheckPosition(entry, new Point(10, 20));
        AgentMovementStuckStateRuntime.setUnstuckCooldownMs(entry, 100);
        AtomicInteger unstucks = new AtomicInteger();

        AgentStuckDetectionService.tickStuckDetection(entry, hooks(unstucks, 500, true, remaining -> remaining));

        assertEquals(500, AgentMovementStuckStateRuntime.stuckMs(entry));
        assertEquals(0, unstucks.get());
    }

    @Test
    void inAirResetsProgressDuringNavigation() {
        AgentRuntimeEntry entry = entryAt(new Point(10, 20));
        AgentMoveTargetStateRuntime.setMoveTarget(entry, new Point(100, 20), false);
        AgentMovementStateRuntime.setInAir(entry, true);
        AgentMovementStuckStateRuntime.addStuckMs(entry, 250);
        AgentMovementStuckStateRuntime.rememberStuckCheckPosition(entry, new Point(10, 20));
        AtomicInteger unstucks = new AtomicInteger();

        AgentStuckDetectionService.tickStuckDetection(entry, hooks(unstucks, 50, true, remaining -> remaining));

        assertEquals(0, AgentMovementStuckStateRuntime.stuckMs(entry));
        assertFalse(AgentMovementStuckStateRuntime.hasStuckCheckPosition(entry));
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

    private static AgentRuntimeEntry entryAt(Point position) {
        return new AgentRuntimeEntry(agentAt(position), mock(Character.class), null);
    }

    private static Character agentAt(Point position) {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(position));
        return agent;
    }
}
