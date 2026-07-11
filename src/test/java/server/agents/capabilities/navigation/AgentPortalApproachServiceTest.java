package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.maps.Foothold;
import server.maps.FootholdTree;
import server.maps.MapleMap;
import server.maps.Portal;
import server.maps.Rope;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPortalApproachServiceTest {
    @Test
    void plainPortalKeepsItsCenter() {
        Portal portal = portal(Portal.MAP_PORTAL, new Point(10, 20));

        assertEquals(new Point(10, 20), AgentPortalApproachService.target(mock(MapleMap.class), portal));
    }

    @Test
    void collisionPortalPrefersRopeInsideHitbox() {
        MapleMap map = new MapleMap(222000001, 0, 0, 222000001, 1.0f);
        map.addRope(new Rope(-51, -600, -300, false));
        Portal portal = portal(AgentPortalApproachService.COLLISION_PORTAL_TYPE, new Point(-31, -463));

        assertEquals(new Point(-51, -463), AgentPortalApproachService.target(map, portal));
    }

    @Test
    void collisionPortalFallsBackToPlatformInsideHitbox() {
        MapleMap map = new MapleMap(910000014, 0, 0, 910000014, 1.0f);
        FootholdTree footholds = new FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(70, 140), new Point(130, 140), 1));
        map.setFootholds(footholds);
        Portal portal = portal(AgentPortalApproachService.COLLISION_PORTAL_TYPE, new Point(100, 100));

        assertEquals(new Point(100, 140), AgentPortalApproachService.target(map, portal));
    }

    private static Portal portal(int type, Point position) {
        Portal portal = mock(Portal.class);
        when(portal.getType()).thenReturn(type);
        when(portal.getPosition()).thenReturn(new Point(position));
        return portal;
    }
}
