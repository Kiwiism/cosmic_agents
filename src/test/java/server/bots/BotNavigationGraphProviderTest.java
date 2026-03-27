package server.bots;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import server.maps.MapleMap;

import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotNavigationGraphProviderTest {
    private static MapleMap henesys;
    private static BotNavigationGraph henesysGraph;
    private static MapleMap ellinia;
    private static BotNavigationGraph elliniaGraph;

    @BeforeAll
    static void loadMaps() {
        System.setProperty("wz-path", Path.of("wz").toAbsolutePath().toString());

        henesys = BotNavigationMapLoader.loadMapGeometry(100000000);
        henesysGraph = BotNavigationGraphProvider.rebuildGraph(henesys);

        ellinia = BotNavigationMapLoader.loadMapGeometry(101000000);
        elliniaGraph = BotNavigationGraphProvider.rebuildGraph(ellinia);
    }

    @Test
    void shouldKeepHenesysLowerTownStreetInOneMergedRegion() {
        int firstRegionId = henesysGraph.findRegionId(henesys, new Point(990, 334));
        int secondRegionId = henesysGraph.findRegionId(henesys, new Point(1080, 334));

        assertEquals(firstRegionId, secondRegionId);
        assertTrue(firstRegionId > 0);
    }

    @Test
    void shouldGenerateDirectHenesysJumpEdgeFromBelowToFoothold315() {
        BotNavigationGraph.Edge edge = findNearbyEdge(henesysGraph, henesys, new Point(1080, 334),
                BotNavigationGraph.EdgeType.JUMP, 24, 16);

        assertNotNull(edge);
        assertEquals(new Point(1080, 334), edge.startPoint);
        assertEquals(new Point(1080, 275), edge.endPoint);
    }

    @Test
    void shouldFindSingleJumpPathFromHenesysStreetToUpperPlatform() {
        List<BotNavigationGraph.Edge> path = findPath(henesysGraph, henesys, new Point(1080, 334), new Point(1275, 275));

        assertEquals(1, path.size());
        assertEquals(BotNavigationGraph.EdgeType.JUMP, path.getFirst().type);
        assertEquals(new Point(1080, 334), path.getFirst().startPoint);
        assertEquals(new Point(1080, 275), path.getFirst().endPoint);
    }

    @Test
    void shouldFindSingleJumpPathFromHenesysStreetToLeftUpperPlatform() {
        List<BotNavigationGraph.Edge> path = findPath(henesysGraph, henesys, new Point(990, 334), new Point(938, 274));

        assertEquals(1, path.size());
        assertEquals(BotNavigationGraph.EdgeType.JUMP, path.getFirst().type);
        assertEquals(new Point(938, 274), path.getFirst().endPoint);
    }

    @Test
    void shouldNotMergeElliniaPivotFootholdsIntoOneRegion() {
        Point leftSlopePoint = new Point(-508, -421);
        Point rightSlopePoint = new Point(-464, -422);

        int leftRegionId = elliniaGraph.findRegionId(ellinia, leftSlopePoint);
        int rightRegionId = elliniaGraph.findRegionId(ellinia, rightSlopePoint);

        assertTrue(leftRegionId > 0);
        assertTrue(rightRegionId > 0);
        assertNotEquals(leftRegionId, rightRegionId);
    }

    @Test
    void shouldKeepElliniaPivotFootholdsWalkConnectedAfterSplit() {
        List<BotNavigationGraph.Edge> path = findPath(elliniaGraph, ellinia, new Point(-508, -421), new Point(-217, -351));

        assertFalse(path.isEmpty());
        assertTrue(path.stream().allMatch(edge -> edge.type == BotNavigationGraph.EdgeType.WALK));
    }

    @Test
    void shouldFindElliniaPathFromLowerPlatformToUpperLeftSlope() {
        List<BotNavigationGraph.Edge> path = findPath(elliniaGraph, ellinia, new Point(-445, -55), new Point(-491, -426));
        int targetRegionId = elliniaGraph.findRegionId(ellinia, new Point(-491, -426));

        assertFalse(path.isEmpty());
        assertEquals(targetRegionId, path.getLast().toRegionId);
        assertTrue(path.stream().anyMatch(edge -> edge.type == BotNavigationGraph.EdgeType.CLIMB));
    }

    @Test
    void shouldGenerateElliniaJumpEdgeFromLowerRightPlatformToPlatformAbove() {
        BotNavigationGraph.Edge edge = findNearbyEdge(elliniaGraph, ellinia, new Point(1355, -888),
                BotNavigationGraph.EdgeType.JUMP, 24, 16);

        assertNotNull(edge);
        assertEquals(new Point(1355, -888), edge.startPoint);
        assertEquals(new Point(1299, -955), edge.endPoint);
    }

    @Test
    void shouldGenerateElliniaClimbEdgesForTownRopes() {
        int climbEdgeCount = countEdges(elliniaGraph, BotNavigationGraph.EdgeType.CLIMB);

        assertTrue(climbEdgeCount > 0, "Ellinia town should expose climb edges for at least some ropes");
    }

    @Test
    void shouldPreferElliniaLocalRightSideJumpChainOverFarLeftDetour() {
        List<BotNavigationGraph.Edge> path = findPath(elliniaGraph, ellinia, new Point(1355, -888), new Point(1354, -1197));
        int targetRegionId = elliniaGraph.findRegionId(ellinia, new Point(1354, -1197));

        assertFalse(path.isEmpty());
        assertTrue(path.stream().allMatch(edge -> edge.type == BotNavigationGraph.EdgeType.JUMP));
        assertEquals(new Point(1355, -888), path.getFirst().startPoint);
        assertEquals(targetRegionId, path.getLast().toRegionId);
    }

    @Test
    void shouldPreferElliniaLedgeDropsOverDownJumpsWhenDroppingStraightDown() {
        List<BotNavigationGraph.Edge> path = findPath(elliniaGraph, ellinia, new Point(1354, -1197), new Point(1355, -888));

        assertEquals(BotNavigationGraph.EdgeType.DROP, path.getFirst().type);
        assertTrue(path.getFirst().launchStepX != 0, "Ledge drops should keep a horizontal walk-off step instead of down-jumping in place");
        assertTrue(path.stream().allMatch(edge -> edge.type == BotNavigationGraph.EdgeType.DROP
                || edge.type == BotNavigationGraph.EdgeType.WALK));
    }

    private static List<BotNavigationGraph.Edge> findPath(BotNavigationGraph graph,
                                                          MapleMap map,
                                                          Point start,
                                                          Point target) {
        int startRegionId = graph.findRegionId(map, start);
        int targetRegionId = graph.findRegionId(map, target);

        assertTrue(startRegionId > 0, "Missing graph region for start point " + start);
        assertTrue(targetRegionId > 0, "Missing graph region for target point " + target);

        return BotNavigationManager.findPath(graph, map, start, startRegionId, targetRegionId, target);
    }

    private static BotNavigationGraph.Edge findNearbyEdge(BotNavigationGraph graph,
                                                          MapleMap map,
                                                          Point point,
                                                          BotNavigationGraph.EdgeType type,
                                                          int maxDx,
                                                          int maxDy) {
        int regionId = graph.findRegionId(map, point);
        assertTrue(regionId > 0, "Missing graph region for point " + point);

        List<BotNavigationGraph.Edge> nearby = new ArrayList<>();
        for (BotNavigationGraph.Edge edge : graph.getOutgoing(regionId)) {
            if (edge.type != type) {
                continue;
            }
            if (Math.abs(edge.startPoint.x - point.x) > maxDx || Math.abs(edge.startPoint.y - point.y) > maxDy) {
                continue;
            }
            nearby.add(edge);
        }

        assertFalse(nearby.isEmpty(), "Missing " + type + " edge near " + point);
        nearby.sort((left, right) -> {
            int leftDistance = Math.abs(left.startPoint.x - point.x) + Math.abs(left.startPoint.y - point.y);
            int rightDistance = Math.abs(right.startPoint.x - point.x) + Math.abs(right.startPoint.y - point.y);
            return Integer.compare(leftDistance, rightDistance);
        });
        return nearby.getFirst();
    }

    private static int countEdges(BotNavigationGraph graph, BotNavigationGraph.EdgeType type) {
        int count = 0;
        for (BotNavigationGraph.Region region : graph.regions) {
            for (BotNavigationGraph.Edge edge : graph.getOutgoing(region.id)) {
                if (edge.type == type) {
                    count++;
                }
            }
        }
        return count;
    }
}
