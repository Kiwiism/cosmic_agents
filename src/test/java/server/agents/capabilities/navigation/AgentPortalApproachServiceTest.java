package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.maps.Foothold;
import server.maps.FootholdTree;
import server.maps.MapleMap;
import server.maps.Portal;
import server.maps.Rope;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPortalApproachServiceTest {
    @Test
    void rightAroundLithHarborEastPortalHasACompleteRouteFromTheMiddlePlatform() {
        System.setProperty("wz-path", "wz");
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(104000100);
        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map);
        Portal portal = map.getPortal("east00");

        Point target = AgentPortalApproachService.navigableTarget(map, portal);
        int targetRegionId = AgentNavigationRegionService.resolvePointTargetRegionId(
                graph, map, target);
        var path = AgentNavigationPathService.findPath(
                graph, map, new Point(1105, 575), 6, targetRegionId, target);

        assertEquals(new Point(2457, 395), target);
        assertEquals(2, targetRegionId);
        assertFalse(path.isEmpty());
        assertEquals(2, path.getLast().toRegionId);
    }

    @Test
    void mushroomKingdomBossDoorHasAReachableApproachFromTheJunctionSpawn() {
        System.setProperty("wz-path", "wz");
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(106021400);
        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map);
        Portal portal = map.getPortal(2);
        Point start = new Point(-190, 141);

        Point target = AgentPortalApproachService.navigableTarget(map, portal);
        int startRegionId = AgentNavigationRegionService.resolvePointTargetRegionId(
                graph, map, start);
        int targetRegionId = AgentNavigationRegionService.resolvePointTargetRegionId(
                graph, map, target);
        var path = AgentNavigationPathService.findPath(
                graph, map, start, startRegionId, targetRegionId, target);

        assertFalse(path.isEmpty());
        assertEquals(targetRegionId, path.getLast().toRegionId);
    }

    @Test
    void plainPortalKeepsItsCenter() {
        Portal portal = portal(Portal.MAP_PORTAL, new Point(10, 20));

        assertEquals(new Point(10, 20), AgentPortalApproachService.target(mock(MapleMap.class), portal));
    }

    @Test
    void ordinaryPortalNavigationGroundsAnAuthoredPointOutsideTheFoothold() {
        MapleMap map = new MapleMap(101040000, 0, 0, 101040000, 1.0f);
        FootholdTree footholds = new FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(-850, 120), new Point(-700, 120), 1));
        map.setFootholds(footholds);
        Portal portal = portal(Portal.MAP_PORTAL, new Point(-800, 90));

        assertEquals(new Point(-800, 120),
                AgentPortalApproachService.navigableTarget(map, portal));
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
