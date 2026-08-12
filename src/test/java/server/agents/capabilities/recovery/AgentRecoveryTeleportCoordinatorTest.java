package server.agents.capabilities.recovery;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

class AgentRecoveryTeleportCoordinatorTest {
    @Test
    void delegatesOnlyWhenAgentIsOutsideKnownMapBounds() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Character agent = mock(Character.class);
        Point target = new Point(20, 30);

        try (MockedStatic<AgentRecoveryTeleportService> service = mockStatic(AgentRecoveryTeleportService.class)) {
            service.when(() -> AgentRecoveryTeleportService.isOutsideKnownMapBounds(agent))
                    .thenReturn(true);
            service.when(() -> AgentRecoveryTeleportService.recoverTeleportDistance(
                            eq(entry), eq(agent), eq(target), eq(4000), eq(600),
                            any(AgentRecoveryTeleportService.RecoveryHooks.class)))
                    .thenReturn(true);

            assertTrue(AgentRecoveryTeleportCoordinator.recoverTeleportDistance(
                    entry, agent, target, 4000, 600));
        }
    }

    @Test
    void inBoundsAgentNeverUsesSoftTeleport() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Character agent = mock(Character.class);

        try (MockedStatic<AgentRecoveryTeleportService> service = mockStatic(AgentRecoveryTeleportService.class)) {
            service.when(() -> AgentRecoveryTeleportService.isOutsideKnownMapBounds(agent))
                    .thenReturn(false);
            assertFalse(AgentRecoveryTeleportCoordinator.recoverTeleportDistance(
                    entry, agent, new Point(20, 30), 4000, 600));
            service.verify(() -> AgentRecoveryTeleportService.recoverTeleportDistance(
                    any(AgentRuntimeEntry.class), any(Character.class), any(Point.class),
                    eq(4000), eq(600), any(AgentRecoveryTeleportService.RecoveryHooks.class)), never());
        }
    }
}
