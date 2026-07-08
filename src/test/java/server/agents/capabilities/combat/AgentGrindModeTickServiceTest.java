package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentGrindNoTargetFallbackService;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentGrindModeTickServiceTest {
    @Test
    void noTargetPathUsesFallbackMovementAndConsumesTick() {
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(mock(MapleMap.class));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Point agentPosition = new Point(50, 25);
        Point currentTarget = new Point(75, 25);
        Point fallbackTarget = new Point(250, 25);
        AtomicInteger movementSteps = new AtomicInteger();
        AtomicReference<Point> movedTo = new AtomicReference<>();

        AgentGrindModeTickService.Result result = AgentGrindModeTickService.tickGrindMode(
                entry,
                agent,
                agentPosition,
                currentTarget,
                false,
                hooks(fallbackTarget, movementSteps, movedTo));

        assertEquals(1, movementSteps.get());
        assertSame(fallbackTarget, movedTo.get());
        assertEquals(new AgentGrindModeTickService.Result(true, fallbackTarget), result);
    }

    private static AgentGrindModeTickService.Hooks hooks(Point fallbackTarget,
                                                         AtomicInteger movementSteps,
                                                         AtomicReference<Point> movedTo) {
        return new AgentGrindModeTickService.Hooks(
                new AgentGrindTargetSearchService.SearchHooks(
                        (entry, agent) -> null,
                        (entry, agent) -> null,
                        250L),
                new AgentGrindNoTargetFallbackService.Hooks(
                        (entry, targetPos) -> {
                            movementSteps.incrementAndGet();
                            movedTo.set(targetPos);
                        },
                        (entry, targetPos) -> {
                            movementSteps.incrementAndGet();
                            movedTo.set(targetPos);
                        },
                        (entry, agentPosition, map) -> fallbackTarget,
                        (entry, agentPosition, map) -> fallbackTarget,
                        (entry, targetPos, runAiTick) -> {
                            movementSteps.incrementAndGet();
                            movedTo.set(targetPos);
                        }),
                new AgentGrindTargetCommitmentService.Hooks(
                        (entry, agent, agentPosition, preferredTarget) -> null,
                        (agent, agentPosition, targetPosition) -> null),
                new AgentGrindRangedEngagementService.Hooks(
                        agent -> null,
                        (weaponType, agentPosition, targetPosition) -> false,
                        (weaponType, agentPosition, targetPosition) -> false,
                        (entry, agentPosition, targetPosition) -> null,
                        (attackPlan, agent, target) -> false,
                        (entry, agent, target, attackPlan, agentPosition) -> null,
                        (grounded, weaponType, route) -> false,
                        (entry, agent, attackPlan) -> {
                        },
                        weaponType -> false,
                        (movementProfile, closeRangeRoute, agentPosition, targetPosition, maxJumpHeight) -> false,
                        movementProfile -> 0.0f,
                        (entry, agent, dx) -> {
                        },
                        (entry, agent) -> {
                        },
                        entry -> {
                        }),
                new AgentGrindNavigationTailService.Hooks(
                        (entry, agentPosition, combatTargetPosition, retreatChecked) -> combatTargetPosition,
                        (weaponType, agentPosition, targetPosition) -> false,
                        (entry, agentPosition, mobPosition) -> null),
                500,
                0);
    }
}
