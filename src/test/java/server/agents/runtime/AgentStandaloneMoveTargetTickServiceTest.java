package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentStandaloneMoveTargetTickServiceTest {
    @Test
    void skipsRefreshAndMovementWhenMapChangeGroundingConsumesTick() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Character agent = mock(Character.class);
        AtomicInteger refreshes = new AtomicInteger();
        AtomicInteger movementSteps = new AtomicInteger();

        AgentStandaloneMoveTargetTickService.tickStandaloneMoveTarget(
                entry,
                agent,
                true,
                new AgentStandaloneMoveTargetTickService.Hooks(
                        (groundEntry, groundAgent) -> true,
                        refreshEntry -> refreshes.incrementAndGet(),
                        (moveEntry, targetPosition, runAiTick) -> movementSteps.incrementAndGet()));

        assertEquals(0, refreshes.get());
        assertEquals(0, movementSteps.get());
    }

    @Test
    void refreshesProfileAndStepsTowardStoredMoveTarget() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Character agent = mock(Character.class);
        Point moveTarget = new Point(120, 45);
        AgentMoveTargetStateRuntime.setMoveTarget(entry, moveTarget, false);
        AtomicInteger refreshes = new AtomicInteger();
        AtomicReference<Point> movementTarget = new AtomicReference<>();
        AtomicReference<Boolean> runAiTickSeen = new AtomicReference<>();

        AgentStandaloneMoveTargetTickService.tickStandaloneMoveTarget(
                entry,
                agent,
                false,
                new AgentStandaloneMoveTargetTickService.Hooks(
                        (groundEntry, groundAgent) -> false,
                        refreshEntry -> refreshes.incrementAndGet(),
                        (moveEntry, targetPosition, runAiTick) -> {
                            movementTarget.set(targetPosition);
                            runAiTickSeen.set(runAiTick);
                        }));

        assertEquals(1, refreshes.get());
        assertEquals(moveTarget, movementTarget.get());
        assertEquals(Boolean.FALSE, runAiTickSeen.get());
    }
}
