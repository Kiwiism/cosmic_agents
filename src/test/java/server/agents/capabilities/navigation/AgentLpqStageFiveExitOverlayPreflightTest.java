package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;
import server.maps.MapleMap;
import server.maps.Portal;
import server.maps.Rope;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLpqStageFiveExitOverlayPreflightTest {
    private static final Map<Integer, List<Integer>> AUTHORED_EXIT_REGIONS = Map.of(
            922_010_502, List.of(10, 12, 6, 13, 3),
            922_010_503, List.of(9, 11, 5, 12, 3),
            922_010_504, List.of(11, 13, 5, 14, 3),
            922_010_505, List.of(9, 11, 5, 12, 3));

    @Test
    void standardRoomsFollowTheirAuthoredFootholdAndRopeSpinesToPortalTwo() {
        for (Map.Entry<Integer, List<Integer>> expected : AUTHORED_EXIT_REGIONS.entrySet()) {
            int mapId = expected.getKey();
            List<Integer> regionPath = expected.getValue();
            MapleMap map = AgentNavigationMapLoader.loadMapGeometry(mapId);
            Portal spawn = map.getPortal(0);
            Portal exit = map.getPortal(2);
            assertNotNull(spawn, () -> "missing spawn portal in " + mapId);
            assertNotNull(exit, () -> "missing portal 2 in " + mapId);
            assertEquals(922_010_500, exit.getTargetMapId());
            assertEquals(new Point(-147, -3_535), exit.getPosition());
            assertEquals(List.of(
                            new Rope(-8, -2_488, -286, false),
                            new Rope(13, -3_533, -2_562, false)),
                    map.getRopes());

            AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map);
            int startRegion = graph.findRegionId(map, spawn.getPosition());
            int targetRegion = graph.findRegionId(map, exit.getPosition());
            assertEquals(59, graph.version);
            assertEquals(regionPath.getFirst(), startRegion);
            assertEquals(regionPath.getLast(), targetRegion);
            assertTrue(AgentNavigationRouteOverlayPolicy.applies(graph, targetRegion));

            for (int index = 0; index < regionPath.size() - 1; index++) {
                int from = regionPath.get(index);
                int to = regionPath.get(index + 1);
                List<AgentNavigationGraph.Edge> outgoing = graph.getOutgoing(from);
                assertTrue(outgoing.stream().anyMatch(edge -> edge.toRegionId == to),
                        () -> "missing authored edge " + from + "->" + to + " in " + mapId);
                for (AgentNavigationGraph.Edge edge : outgoing) {
                    assertEquals(edge.toRegionId == to,
                            AgentNavigationRouteOverlayPolicy.allows(graph, targetRegion, edge),
                            () -> "overlay mismatch for " + from + "->" + edge.toRegionId
                                    + " in " + mapId);
                }
            }

            List<AgentNavigationGraph.Edge> path = AgentNavigationPathService.findPath(
                    graph, map, spawn.getPosition(), startRegion,
                    targetRegion, exit.getPosition(), "lpq-stage5-exit-preflight");
            assertFalse(path.isEmpty(), () -> "no authored exit path in " + mapId);
            assertEquals(regionPath.subList(1, regionPath.size()),
                    path.stream().map(edge -> edge.toRegionId).toList());

            Data mapData = DataProviderFactory.getDataProvider(WZFiles.MAP)
                    .getData("Map/Map9/" + mapId + ".img");
            assertNotNull(mapData);
            Data reactors = mapData.getChildByPath("reactor");
            assertNotNull(reactors);
            assertEquals(4, reactors.getChildren().size());
            for (Data reactor : reactors) {
                Point boxPosition = new Point(
                        DataTool.getInt("x", reactor, 0),
                        DataTool.getInt("y", reactor, 0));
                int boxRegion = graph.findRegionId(map, boxPosition);
                List<AgentNavigationGraph.Edge> boxExit = AgentNavigationPathService.findPath(
                        graph, map, boxPosition, boxRegion,
                        targetRegion, exit.getPosition(), "lpq-stage5-box-exit-preflight");
                assertFalse(boxExit.isEmpty(), () -> "box beside " + boxPosition
                        + " cannot reach portal 2 in " + mapId);

                List<Integer> visited = new ArrayList<>();
                visited.add(boxRegion);
                boxExit.forEach(edge -> visited.add(edge.toRegionId));
                int mergeAt = -1;
                int authoredAt = -1;
                for (int index = 0; index < visited.size(); index++) {
                    int candidate = regionPath.indexOf(visited.get(index));
                    if (candidate >= 0) {
                        mergeAt = index;
                        authoredAt = candidate;
                        break;
                    }
                }
                assertTrue(mergeAt >= 0, () -> "box route never joins authored spine in " + mapId);
                assertEquals(regionPath.subList(authoredAt, regionPath.size()),
                        visited.subList(mergeAt, visited.size()),
                        () -> "box route leaves authored spine in " + mapId);
            }
        }
    }

    @Test
    void darkSightRoomFollowsItsAuthoredEightRopeSpineToPortalTwo() {
        int mapId = 922_010_506;
        List<Integer> regions = List.of(
                11, 14, 10, 15, 9, 16, 8, 17, 7, 18, 6, 19, 5, 20, 4, 13, 3);
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(mapId);
        Portal spawn = map.getPortal(0);
        Portal exit = map.getPortal(2);
        assertNotNull(spawn);
        assertNotNull(exit);
        assertEquals(922_010_500, exit.getTargetMapId());

        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map);
        int startRegion = graph.findRegionId(map, spawn.getPosition());
        int targetRegion = graph.findRegionId(map, exit.getPosition());
        assertEquals(regions.getFirst(), startRegion);
        assertEquals(regions.getLast(), targetRegion);
        assertTrue(AgentNavigationRouteOverlayPolicy.applies(graph, targetRegion));
        for (int index = 0; index < regions.size() - 1; index++) {
            int from = regions.get(index);
            int to = regions.get(index + 1);
            List<AgentNavigationGraph.Edge> outgoing = graph.getOutgoing(from);
            assertTrue(outgoing.stream().anyMatch(edge -> edge.toRegionId == to));
            for (AgentNavigationGraph.Edge edge : outgoing) {
                assertEquals(edge.toRegionId == to,
                        AgentNavigationRouteOverlayPolicy.allows(graph, targetRegion, edge));
            }
        }
        List<AgentNavigationGraph.Edge> path = AgentNavigationPathService.findPath(
                graph, map, spawn.getPosition(), startRegion,
                targetRegion, exit.getPosition(), "lpq-stage5-dark-sight-exit-preflight");
        assertEquals(regions.subList(1, regions.size()),
                path.stream().map(edge -> edge.toRegionId).toList());
    }
}
