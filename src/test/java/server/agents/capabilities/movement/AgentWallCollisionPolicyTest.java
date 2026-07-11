package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationWalkRegionLookupService;
import server.maps.Foothold;
import server.maps.FootholdTree;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWallCollisionPolicyTest {
    @Test
    void scopesWallsToBaseAndMoverZMassGroups() {
        MapleMap map = mapWithFootholds(
                foothold(1, 0, 100, 200, 100, 0),
                foothold(2, 50, 0, 50, 100, 0),
                foothold(3, 75, 0, 75, 100, 1),
                foothold(4, 100, 0, 100, 100, 2));
        Map<Integer, Foothold> byId = index(map);

        assertTrue(AgentWallCollisionPolicy.collides(map, byId.get(2), 2));
        assertFalse(AgentWallCollisionPolicy.collides(map, byId.get(3), 2));
        assertTrue(AgentWallCollisionPolicy.collides(map, byId.get(4), 2));
        assertTrue(AgentWallCollisionPolicy.collides(map, byId.get(2), AgentWallCollisionPolicy.UNKNOWN_GROUP));
        assertFalse(AgentWallCollisionPolicy.collides(map, byId.get(4), AgentWallCollisionPolicy.UNKNOWN_GROUP));
    }

    @Test
    void unknownSyntheticMetadataPreservesConservativeCollision() {
        MapleMap map = mapWithFootholds(new Foothold(new Point(50, 0), new Point(50, 100), 1));

        assertTrue(AgentWallCollisionPolicy.collides(
                map, map.getFootholds().getAllFootholds().getFirst(), AgentWallCollisionPolicy.UNKNOWN_GROUP));
    }

    @Test
    void regionConstrainedGroundWalkingDoesNotScanWalls() {
        Foothold ground = foothold(1, 0, 100, 100, 100, 0);
        Foothold wall = foothold(2, 50, 0, 50, 100, 0);
        MapleMap map = mapWithFootholds(ground, wall);
        AgentNavigationGraph.Region region = new AgentNavigationGraph.Region(
                1, List.of(new AgentNavigationGraph.Segment(ground)));
        AgentNavigationWalkRegionLookupService.setBuildWalkRegionLookup(
                map, Map.of(1, region), Map.of(1, 1), Map.of(1, ground, 2, wall));
        try {
            assertTrue(AgentGroundCollisionService.canWalkGroundStep(
                    map, new Point(44, 100), ground, 12));
            assertFalse(AgentGroundCollisionService.isGroundStepBlockedByWall(
                    map, new Point(44, 100), ground, 12));
        } finally {
            AgentNavigationWalkRegionLookupService.clearBuildWalkRegionLookup();
        }
    }

    private static Foothold foothold(int id, int x1, int y1, int x2, int y2, int zMass) {
        Foothold foothold = new Foothold(new Point(x1, y1), new Point(x2, y2), id);
        foothold.setZMass(zMass);
        return foothold;
    }

    private static MapleMap mapWithFootholds(Foothold... footholds) {
        MapleMap map = new MapleMap(910000070, 0, 0, 910000070, 1.0f);
        FootholdTree tree = new FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        for (Foothold foothold : footholds) {
            tree.insert(foothold);
        }
        map.setFootholds(tree);
        return map;
    }

    private static Map<Integer, Foothold> index(MapleMap map) {
        return map.getFootholds().getAllFootholds().stream()
                .collect(java.util.stream.Collectors.toMap(Foothold::getId, foothold -> foothold));
    }
}
