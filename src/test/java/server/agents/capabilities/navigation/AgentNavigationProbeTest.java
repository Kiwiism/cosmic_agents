package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNavigationProbeTest {
    @Test
    void formatsLastBuildReportForLoadedMap() {
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(100000000);

        List<String> lines = AgentNavigationProbe.lastBuildReport(map);

        assertFalse(lines.isEmpty());
    }

    @Test
    void treeThatGrewTwoBuildsBidirectionalLadderConnections() {
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(101010101);
        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map);
        List<AgentNavigationGraph.Region> ladders = graph.regions.stream()
                .filter(region -> region.isRopeRegion && region.isLadder)
                .toList();

        assertEquals(4, ladders.size(), "the authored map contains four ladder connectors");
        for (AgentNavigationGraph.Region ladder : ladders) {
            long exits = graph.getOutgoing(ladder.id).stream()
                    .filter(edge -> edge.type == AgentNavigationGraph.EdgeType.CLIMB)
                    .filter(edge -> !graph.getRegion(edge.toRegionId).isRopeRegion)
                    .count();
            long entries = graph.regions.stream()
                    .filter(region -> !region.isRopeRegion)
                    .flatMap(region -> graph.getOutgoing(region.id).stream())
                    .filter(edge -> edge.type == AgentNavigationGraph.EdgeType.CLIMB)
                    .filter(edge -> edge.toRegionId == ladder.id)
                    .count();

            assertTrue(entries >= 2, "each ladder must be enterable from both adjoining tiers");
            assertTrue(exits >= 2, "each ladder must exit onto both adjoining tiers");
        }
    }

    @Test
    void forestEastOfHenesysConnectsEveryQuestMobHabitat() {
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(100030000);
        AgentNavigationGraph graph = AgentNavigationGraphService.getGraph(map);
        List<Point> habitatPoints = List.of(
                new Point(-3000, -1500), // Pig + Ribbon Pig
                new Point(-4000, -1300), // Pig + Ribbon Pig
                new Point(-3667, -1267), // Pig
                new Point(-3000, -1070), // Pig + Orange Mushroom
                new Point(-3700, -970),  // all three quest mobs
                new Point(-2200, -1220), // Pig + Orange Mushroom
                new Point(-3000, -590),  // Orange Mushroom
                new Point(-4071, -464),  // Orange Mushroom
                new Point(-3700, -530),  // Orange Mushroom
                new Point(-2200, -540),  // Orange Mushroom
                new Point(-3300, -20),   // Pig
                new Point(-3590, 45),    // Pig
                new Point(-2700, 230));  // Pig + Ribbon Pig
        List<Integer> habitatRegions = habitatPoints.stream()
                .map(point -> graph.findRegionId(map, point))
                .distinct()
                .toList();

        assertFalse(habitatRegions.contains(-1), "every quest spawn must resolve to a graph region");
        List<String> unreachable = new ArrayList<>();
        for (int fromRegionId : habitatRegions) {
            Point start = graph.getRegion(fromRegionId).centerPoint();
            for (int toRegionId : habitatRegions) {
                if (fromRegionId == toRegionId) {
                    continue;
                }
                Point target = graph.getRegion(toRegionId).centerPoint();
                List<AgentNavigationGraph.Edge> path = AgentNavigationPathService.findPath(
                        graph, map, start, fromRegionId, toRegionId, target);
                if (path.isEmpty() || path.getLast().toRegionId != toRegionId) {
                    unreachable.add(fromRegionId + "->" + toRegionId);
                }
            }
        }

        assertTrue(unreachable.isEmpty(), "quest habitats need complete directed routes: " + unreachable);
    }

    @Test
    void generatedClimbEdgesIdentifyTheirRope() {
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(100030000);
        AgentNavigationGraph graph = AgentNavigationGraphService.getGraph(map);
        List<AgentNavigationGraph.Edge> climbEdges = graph.regions.stream()
                .flatMap(region -> graph.getOutgoing(region.id).stream())
                .filter(edge -> edge.type == AgentNavigationGraph.EdgeType.CLIMB)
                .toList();

        assertFalse(climbEdges.isEmpty());
        assertTrue(climbEdges.stream().allMatch(edge ->
                        edge.ropeX != 0 && edge.ropeTopY < edge.ropeBottomY),
                "climb validation and suppression require concrete rope identity");
    }

    @Test
    void forestEastRope81UsesTheExecutableIntermediateRouteToRegion31() {
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(100030000);
        AgentNavigationGraph graph = AgentNavigationGraphService.getGraph(map);

        List<AgentNavigationGraph.Edge> path = AgentNavigationPathService.findPath(
                graph,
                map,
                graph.getRegion(81).centerPoint(),
                81,
                31,
                graph.getRegion(31).centerPoint());

        assertEquals(List.of(44, 92, 31), path.stream().map(edge -> edge.toRegionId).toList());
        assertEquals(new Point(-2391, -182), path.getFirst().startPoint,
                "region 81 must leave from its mid-rope jump anchor rather than its unreachable head");
        assertFalse(graph.getOutgoing(81).stream().anyMatch(edge -> edge.toRegionId == 31),
                "the current/destination pair 81->31 must not be mistaken for a direct graph edge");
    }
}
