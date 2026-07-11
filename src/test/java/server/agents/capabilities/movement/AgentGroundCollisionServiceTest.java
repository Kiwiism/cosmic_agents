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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentGroundCollisionServiceTest {
    @Test
    void nullMapGroundQueriesPreserveLegacySafeDefaults() {
        Point point = new Point(10, 20);

        assertFalse(AgentGroundCollisionService.canWalkGroundStep(null, point, 8));
        assertFalse(AgentGroundCollisionService.isGroundStepBlockedByWall(null, point, 8));
        assertFalse(AgentGroundCollisionService.canStartDownJump(null, point));
        assertTrue(AgentGroundCollisionService.isGroundFarBelow(null, point));
    }

    @Test
    void blocksGroundStepThroughCollidableWall() {
        MapleMap map = createEmptyTestMap(910000049);
        FootholdTree footholds = map.getFootholds();
        Foothold lower = new Foothold(new Point(0, 100), new Point(50, 100), 1);
        Foothold wall = new Foothold(new Point(50, 60), new Point(50, 100), 2);
        Foothold upper = new Foothold(new Point(50, 60), new Point(120, 60), 3);
        wall.setNext(lower.getId());
        wall.setPrev(upper.getId());
        footholds.insert(lower);
        footholds.insert(wall);
        footholds.insert(upper);

        assertFalse(AgentGroundCollisionService.canWalkGroundStep(map, new Point(44, 100), 12));
        assertTrue(AgentGroundCollisionService.isGroundStepBlockedByWall(map, new Point(44, 100), 12));
    }

    @Test
    void allowsShortCollidableWallEndpointWithinSlopeLimit() {
        MapleMap map = createEmptyTestMap(910000054);
        FootholdTree footholds = map.getFootholds();
        Foothold lower = new Foothold(new Point(0, 100), new Point(50, 100), 1);
        Foothold wall = new Foothold(new Point(50, 80), new Point(50, 100), 2);
        Foothold upper = new Foothold(new Point(50, 80), new Point(120, 80), 3);
        wall.setNext(lower.getId());
        wall.setPrev(upper.getId());
        footholds.insert(lower);
        footholds.insert(wall);
        footholds.insert(upper);

        assertTrue(AgentGroundCollisionService.canWalkGroundStep(map, new Point(44, 100), 12));
        assertFalse(AgentGroundCollisionService.isGroundStepBlockedByWall(map, new Point(44, 100), 12));
    }

    @Test
    void treatsWallTopLevelWithGroundAsLedgeEdge() {
        MapleMap map = createEmptyTestMap(910000055);
        FootholdTree footholds = map.getFootholds();
        Foothold upper = new Foothold(new Point(0, 80), new Point(50, 80), 1);
        Foothold wall = new Foothold(new Point(50, 80), new Point(50, 140), 2);
        Foothold lower = new Foothold(new Point(50, 140), new Point(120, 140), 3);
        wall.setPrev(upper.getId());
        wall.setNext(lower.getId());
        footholds.insert(upper);
        footholds.insert(wall);
        footholds.insert(lower);

        assertFalse(AgentGroundCollisionService.isGroundStepBlockedByWall(map, new Point(44, 80), 12));
    }

    @Test
    void runwayWallCheckPreservesGroundWallCollisionRules() {
        MapleMap map = createEmptyTestMap(910000057);
        FootholdTree footholds = map.getFootholds();
        Foothold lower = new Foothold(new Point(0, 100), new Point(50, 100), 1);
        Foothold wall = new Foothold(new Point(50, 100), new Point(50, 60), 2);
        wall.setPrev(lower.getId());
        footholds.insert(lower);
        footholds.insert(wall);

        assertTrue(AgentGroundCollisionService.isGroundRunwayBlockedByWall(
                map, new Point(44, 100), new Point(56, 100)));
    }

    @Test
    void followsStandingFootholdChainAcrossJoinedFork() {
        MapleMap map = createEmptyTestMap(910000058);
        Foothold standing = new Foothold(new Point(0, 100), new Point(50, 90), 1);
        Foothold ridge = new Foothold(new Point(50, 90), new Point(100, 50), 2);
        Foothold lowerSpur = new Foothold(new Point(50, 90), new Point(100, 90), 3);
        standing.setNext(ridge.getId());
        ridge.setPrev(standing.getId());
        map.getFootholds().insert(standing);
        map.getFootholds().insert(ridge);
        map.getFootholds().insert(lowerSpur);

        AgentNavigationGraph.Region region = new AgentNavigationGraph.Region(11, List.of(
                new AgentNavigationGraph.Segment(standing),
                new AgentNavigationGraph.Segment(ridge),
                new AgentNavigationGraph.Segment(lowerSpur)));
        AgentNavigationWalkRegionLookupService.setBuildWalkRegionLookup(
                map,
                Map.of(region.id, region),
                Map.of(standing.getId(), region.id, ridge.getId(), region.id, lowerSpur.getId(), region.id),
                Map.of(standing.getId(), standing, ridge.getId(), ridge, lowerSpur.getId(), lowerSpur));
        try {
            Point selected = AgentGroundCollisionService.findWalkRegionGroundPoint(
                    map, standing, 60, 90);

            assertTrue(selected.y < lowerSpur.getY1());
            assertEquals(new Point(60, 82), selected);
        } finally {
            AgentNavigationWalkRegionLookupService.clearBuildWalkRegionLookup();
        }
    }

    private static MapleMap createEmptyTestMap(int mapId) {
        MapleMap map = new MapleMap(mapId, 0, 0, mapId, 1.0f);
        map.setFootholds(new FootholdTree(new Point(-2000, -2000), new Point(2000, 2000)));
        return map;
    }
}
