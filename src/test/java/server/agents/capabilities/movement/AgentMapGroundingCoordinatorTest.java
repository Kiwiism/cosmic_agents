package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentMapGroundingCoordinatorTest {
    @Test
    void delegatesGroundingThroughMovementCapabilityHooks() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Character agent = mock(Character.class);

        try (MockedStatic<AgentMapTransitionService> service = mockStatic(AgentMapTransitionService.class)) {
            service.when(() -> AgentMapTransitionService.groundAfterMapChange(
                            eq(entry),
                            eq(agent),
                            any(AgentMapTransitionService.GroundingHooks.class)))
                    .thenReturn(true);

            assertTrue(AgentMapGroundingCoordinator.groundAfterMapChange(entry, agent));

            service.verify(() -> AgentMapTransitionService.groundAfterMapChange(
                    eq(entry),
                    eq(agent),
                    any(AgentMapTransitionService.GroundingHooks.class)));
        }
    }
}
