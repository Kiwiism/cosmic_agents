package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;
import server.agents.integration.AgentScriptTaskStateRuntime;
import server.agents.plans.AgentTask;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentScriptedMoveCombatTickServiceTest {
    @Test
    void fallsThroughWhenNoActiveLocalOpportunityMoveTaskExists() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Character agent = mock(Character.class);
        AtomicInteger attacks = new AtomicInteger();
        AtomicInteger movementSteps = new AtomicInteger();

        AgentScriptedMoveCombatTickService.Result result = AgentScriptedMoveCombatTickService.tickScriptedMoveCombat(
                entry,
                agent,
                new Point(0, 0),
                new Point(100, 0),
                true,
                hooks(attacks, movementSteps, new AtomicReference<>(), new Point(50, 0), false));

        assertFalse(result.consumedTick());
        assertEquals(new Point(100, 0), result.targetPos());
        assertEquals(0, attacks.get());
        assertEquals(0, movementSteps.get());
    }

    @Test
    void consumesWhenLocalOpportunityAttackConsumes() {
        AgentRuntimeEntry entry = localOpportunityMoveEntry(new Point(100, 0));
        Character agent = mock(Character.class);
        AtomicInteger attacks = new AtomicInteger();
        AtomicInteger movementSteps = new AtomicInteger();
        Point attackTarget = new Point(80, 0);

        AgentScriptedMoveCombatTickService.Result result = AgentScriptedMoveCombatTickService.tickScriptedMoveCombat(
                entry,
                agent,
                new Point(0, 0),
                new Point(100, 0),
                true,
                hooks(attacks, movementSteps, new AtomicReference<>(), attackTarget, true));

        assertTrue(result.consumedTick());
        assertEquals(attackTarget, result.targetPos());
        assertEquals(1, attacks.get());
        assertEquals(0, movementSteps.get());
    }

    @Test
    void stepsMovementCoreAfterAttackFallsThrough() {
        AgentRuntimeEntry entry = localOpportunityMoveEntry(new Point(100, 0));
        Character agent = mock(Character.class);
        AtomicInteger attacks = new AtomicInteger();
        AtomicInteger movementSteps = new AtomicInteger();
        AtomicReference<Point> movedTo = new AtomicReference<>();
        Point attackTarget = new Point(80, 0);

        AgentScriptedMoveCombatTickService.Result result = AgentScriptedMoveCombatTickService.tickScriptedMoveCombat(
                entry,
                agent,
                new Point(0, 0),
                new Point(100, 0),
                true,
                hooks(attacks, movementSteps, movedTo, attackTarget, false));

        assertTrue(result.consumedTick());
        assertEquals(attackTarget, result.targetPos());
        assertEquals(1, attacks.get());
        assertEquals(1, movementSteps.get());
        assertEquals(attackTarget, movedTo.get());
    }

    private static AgentRuntimeEntry localOpportunityMoveEntry(Point target) {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AgentScriptTaskStateRuntime.queueTask(entry, AgentTask.moveTo(
                target,
                true,
                AgentTask.MoveCombatMode.LOCAL_OPPORTUNITY));
        AgentScriptTaskStateRuntime.activateNextTask(entry);
        AgentMoveTargetStateRuntime.setMoveTarget(entry, target, true);
        return entry;
    }

    private static AgentScriptedMoveCombatTickService.Hooks hooks(AtomicInteger attacks,
                                                                  AtomicInteger movementSteps,
                                                                  AtomicReference<Point> movedTo,
                                                                  Point attackTarget,
                                                                  boolean attackConsumes) {
        return new AgentScriptedMoveCombatTickService.Hooks(
                (entry, agentPosition, targetPosition) -> {
                },
                (entry, agent, agentPosition, currentTargetPosition) -> {
                    attacks.incrementAndGet();
                    return new AgentScriptedMoveCombatTickService.Result(attackConsumes, attackTarget);
                },
                (entry, targetPosition, runAiTick) -> {
                    movementSteps.incrementAndGet();
                    movedTo.set(targetPosition);
                });
    }
}
