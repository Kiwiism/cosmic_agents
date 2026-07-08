package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentMovementTickRuntimeTest {
    @Test
    void configBoundOverloadDelegatesToMovementTickService() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Point target = new Point(10, 20);

        try (MockedStatic<AgentMovementTickService> service = mockStatic(AgentMovementTickService.class)) {
            service.when(() -> AgentMovementTickService.stepMovementCore(
                            eq(entry),
                            eq(target),
                            eq(true),
                            any(AgentMovementTickService.MovementTickHooks.class)))
                    .thenAnswer(invocation -> null);

            AgentMovementTickRuntime.stepMovementCore(entry, target, true);

            service.verify(() -> AgentMovementTickService.stepMovementCore(
                    eq(entry),
                    eq(target),
                    eq(true),
                    any(AgentMovementTickService.MovementTickHooks.class)));
        }
    }
}
