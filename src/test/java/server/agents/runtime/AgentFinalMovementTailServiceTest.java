package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentFinalMovementTailServiceTest {
    @Test
    void delegatesTargetAndAiFlagToMovementCore() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Point target = new Point(120, 35);
        AtomicInteger steps = new AtomicInteger();
        AtomicReference<Point> targetSeen = new AtomicReference<>();
        AtomicReference<Boolean> aiSeen = new AtomicReference<>();

        AgentFinalMovementTailService.stepFinalMovement(
                entry,
                target,
                true,
                new AgentFinalMovementTailService.Hooks((moveEntry, targetPosition, runAiTick) -> {
                    steps.incrementAndGet();
                    targetSeen.set(targetPosition);
                    aiSeen.set(runAiTick);
                }));

        assertEquals(1, steps.get());
        assertEquals(target, targetSeen.get());
        assertEquals(Boolean.TRUE, aiSeen.get());
    }
}
