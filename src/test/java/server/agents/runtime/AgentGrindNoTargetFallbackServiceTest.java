package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotGrindTargetStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;
import server.life.Monster;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentGrindNoTargetFallbackServiceTest {
    @Test
    void groundFallbackClearsTargetResolvesNoGrindTargetAndStepsMovement() {
        MapleMap map = mock(MapleMap.class);
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(map);
        BotEntry entry = new BotEntry(agent, mock(Character.class), null);
        Monster staleTarget = mock(Monster.class);
        AgentBotGrindTargetStateRuntime.setTarget(entry, staleTarget);
        Point resolved = new Point(300, 100);
        AtomicReference<Point> steppedTarget = new AtomicReference<>();
        AtomicInteger noGrindResolves = new AtomicInteger();

        AgentGrindNoTargetFallbackService.Result result =
                AgentGrindNoTargetFallbackService.handleNoTarget(
                        entry,
                        agent,
                        new Point(100, 100),
                        new Point(150, 100),
                        true,
                        new AgentGrindNoTargetFallbackService.Hooks(
                                (ignoredEntry, ignoredTarget) -> {
                                },
                                (ignoredEntry, ignoredTarget) -> {
                                },
                                (ignoredEntry, ignoredPosition, ignoredMap) -> new Point(-1, -1),
                                (ignoredEntry, ignoredPosition, ignoredMap) -> {
                                    noGrindResolves.incrementAndGet();
                                    return resolved;
                                },
                                (ignoredEntry, targetPos, runAiTick) -> steppedTarget.set(targetPos)));

        assertTrue(result.consumedTick());
        assertEquals(resolved, result.targetPos());
        assertEquals(resolved, steppedTarget.get());
        assertEquals(1, noGrindResolves.get());
        assertNull(AgentBotGrindTargetStateRuntime.target(entry));
    }

    @Test
    void airborneFallbackTicksAirborneAndDoesNotStepMovement() {
        Character agent = mock(Character.class);
        BotEntry entry = new BotEntry(agent, mock(Character.class), null);
        AgentBotMovementStateRuntime.setInAir(entry, true);
        Point currentTarget = new Point(150, 100);
        AtomicInteger airborneTicks = new AtomicInteger();
        AtomicInteger movementSteps = new AtomicInteger();

        AgentGrindNoTargetFallbackService.Result result =
                AgentGrindNoTargetFallbackService.handleNoTarget(
                        entry,
                        agent,
                        new Point(100, 100),
                        currentTarget,
                        true,
                        new AgentGrindNoTargetFallbackService.Hooks(
                                (ignoredEntry, ignoredTarget) -> {
                                },
                                (ignoredEntry, ignoredTarget) -> airborneTicks.incrementAndGet(),
                                (ignoredEntry, ignoredPosition, ignoredMap) -> new Point(-1, -1),
                                (ignoredEntry, ignoredPosition, ignoredMap) -> new Point(-2, -2),
                                (ignoredEntry, targetPos, runAiTick) -> movementSteps.incrementAndGet()));

        assertTrue(result.consumedTick());
        assertEquals(currentTarget, result.targetPos());
        assertEquals(1, airborneTicks.get());
        assertEquals(0, movementSteps.get());
    }
}
