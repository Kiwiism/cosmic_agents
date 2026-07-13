package server.agents.integration.cosmic;

import client.Character;
import org.junit.jupiter.api.Test;
import server.maps.MapleMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CosmicMapGatewayTest {
    @Test
    void swimMapQueryHandlesMissingAgentAndMap() {
        assertFalse(CosmicMapGateway.INSTANCE.isSwimMap(null));

        Character agent = mock(Character.class);
        assertFalse(CosmicMapGateway.INSTANCE.isSwimMap(agent));
    }

    @Test
    void swimMapQueryReadsCosmicMapFlag() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(agent.getMap()).thenReturn(map);
        when(map.isSwim()).thenReturn(true);

        assertTrue(CosmicMapGateway.INSTANCE.isSwimMap(agent));
    }

    @Test
    void observerQueryReadsCosmicMapSnapshot() {
        MapleMap map = mock(MapleMap.class);
        when(map.isObservedByPlayer()).thenReturn(true);

        assertFalse(CosmicMapGateway.INSTANCE.isObservedByPlayer(null));
        assertTrue(CosmicMapGateway.INSTANCE.isObservedByPlayer(map));
    }
}
