package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentModeStateRuntime;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentGrindModeDispatchServiceTest {
    @Test
    void fallsThroughWhenNotGrinding() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Point target = new Point(10, 20);
        AtomicInteger grindTicks = new AtomicInteger();

        AgentGrindModeDispatchService.Result result = AgentGrindModeDispatchService.tickIfGrinding(
                entry,
                mock(Character.class),
                new Point(0, 0),
                target,
                true,
                new AgentGrindModeDispatchService.Hooks((grindEntry, agent, agentPosition, targetPosition, runAiTick) -> {
                    grindTicks.incrementAndGet();
                    return new AgentGrindModeDispatchService.Result(true, new Point(99, 99));
                }));

        assertFalse(result.consumedTick());
        assertEquals(target, result.targetPos());
        assertEquals(0, grindTicks.get());
    }

    @Test
    void delegatesWhenGrindingAndReturnsResult() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AgentModeStateRuntime.setGrinding(entry, true);
        Point updatedTarget = new Point(80, 10);
        AtomicInteger grindTicks = new AtomicInteger();

        AgentGrindModeDispatchService.Result result = AgentGrindModeDispatchService.tickIfGrinding(
                entry,
                mock(Character.class),
                new Point(0, 0),
                new Point(10, 20),
                false,
                new AgentGrindModeDispatchService.Hooks((grindEntry, agent, agentPosition, targetPosition, runAiTick) -> {
                    grindTicks.incrementAndGet();
                    assertFalse(runAiTick);
                    return new AgentGrindModeDispatchService.Result(true, updatedTarget);
                }));

        assertTrue(result.consumedTick());
        assertEquals(updatedTarget, result.targetPos());
        assertEquals(1, grindTicks.get());
    }
}
