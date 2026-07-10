package server.agents.capabilities.recovery;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentRecoveryTeleportCoordinatorTest {
    @Test
    void delegatesDistanceRecoveryWithMovementHooks() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Character agent = mock(Character.class);
        Point target = new Point(20, 30);

        try (MockedStatic<AgentRecoveryTeleportService> service = mockStatic(AgentRecoveryTeleportService.class)) {
            service.when(() -> AgentRecoveryTeleportService.recoverTeleportDistance(
                            eq(entry),
                            eq(agent),
                            eq(target),
                            eq(4000),
                            eq(600),
                            any(AgentRecoveryTeleportService.RecoveryHooks.class)))
                    .thenReturn(true);

            assertTrue(AgentRecoveryTeleportCoordinator.recoverTeleportDistance(
                    entry, agent, target, 4000, 600));

            service.verify(() -> AgentRecoveryTeleportService.recoverTeleportDistance(
                    eq(entry),
                    eq(agent),
                    eq(target),
                    eq(4000),
                    eq(600),
                    any(AgentRecoveryTeleportService.RecoveryHooks.class)));
        }
    }
}
