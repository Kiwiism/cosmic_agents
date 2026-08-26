package server.agents.integration.cosmic;

import client.Character;
import org.junit.jupiter.api.Test;
import scripting.event.EventInstanceManager;
import server.maps.MapleMap;
import server.maps.Portal;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Test
    void changeMapNearPreservesMembershipWithinTheSameEventInstance() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        Portal portal = mock(Portal.class);
        EventInstanceManager event = mock(EventInstanceManager.class);
        Point destination = new Point(10, 20);
        when(agent.getEventInstance()).thenReturn(event);
        when(map.getEventInstance()).thenReturn(event);
        when(map.findClosestPortal(destination)).thenReturn(portal);

        CosmicMapGateway.INSTANCE.changeMapNear(agent, map, destination);

        verify(agent).changeMap(map, portal);
        verify(agent, never()).forceChangeMap(map, portal);
    }
}
