package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.maps.MapleMap;

import java.awt.Point;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentSpawnPlacementCoordinatorTest {
    @Test
    void delegatesOnlinePlacementThroughSpawnHooks() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        Point position = new Point(20, 30);

        try (MockedStatic<AgentSpawnPlacementService> service = mockStatic(AgentSpawnPlacementService.class)) {
            service.when(() -> AgentSpawnPlacementService.placeSpawnedOnlineAgent(
                            eq(entry),
                            eq(agent),
                            eq(map),
                            eq(position),
                            any(AgentSpawnPlacementService.Hooks.class)))
                    .thenAnswer(invocation -> null);

            AgentSpawnPlacementCoordinator.placeSpawnedOnlineAgent(entry, agent, map, position);

            service.verify(() -> AgentSpawnPlacementService.placeSpawnedOnlineAgent(
                    eq(entry),
                    eq(agent),
                    eq(map),
                    eq(position),
                    any(AgentSpawnPlacementService.Hooks.class)));
        }
    }
}
