package server.agents.integration.cosmic;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.runtime.AgentOfflineLoadService;
import server.maps.MapleMap;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class CosmicAgentOfflineLoaderTest {
    @Test
    void delegatesOfflineLoadThroughCosmicHooks() throws Exception {
        MapleMap map = mock(MapleMap.class);
        Point position = new Point(20, 30);
        Character loaded = mock(Character.class);
        AgentOfflineLoadService.Hooks hooks = mock(AgentOfflineLoadService.Hooks.class);

        try (MockedStatic<AgentOfflineLoadService> service = mockStatic(AgentOfflineLoadService.class)) {
            service.when(() -> AgentOfflineLoadService.loadOfflineAgent(
                            eq(100), eq(1), eq(2), eq(map), eq(position), eq(hooks)))
                    .thenReturn(loaded);

            assertSame(loaded, CosmicAgentOfflineLoader.loadOfflineAgent(
                    100, 1, 2, map, position, hooks));

            service.verify(() -> AgentOfflineLoadService.loadOfflineAgent(
                    eq(100), eq(1), eq(2), eq(map), eq(position), eq(hooks)));
        }
    }
}
