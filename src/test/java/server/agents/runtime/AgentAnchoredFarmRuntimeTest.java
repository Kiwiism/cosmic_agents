package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Point;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentAnchoredFarmRuntimeTest {
    @Test
    void configBoundOverloadDelegatesToAnchoredFarmService() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Character agent = mock(Character.class);
        Point position = new Point(10, 20);

        try (MockedStatic<AgentAnchoredFarmTickService> service = mockStatic(AgentAnchoredFarmTickService.class)) {
            service.when(() -> AgentAnchoredFarmTickService.tickAnchoredFarm(
                            eq(entry),
                            eq(agent),
                            eq(position),
                            eq(true),
                            any(AgentAnchoredFarmTickService.AnchoredFarmHooks.class)))
                    .thenAnswer(invocation -> null);

            AgentAnchoredFarmRuntime.tickAnchoredFarm(entry, agent, position, true);

            service.verify(() -> AgentAnchoredFarmTickService.tickAnchoredFarm(
                    eq(entry),
                    eq(agent),
                    eq(position),
                    eq(true),
                    any(AgentAnchoredFarmTickService.AnchoredFarmHooks.class)));
        }
    }
}
