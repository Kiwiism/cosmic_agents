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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLpqStageFourExitOverlayPreflightTest {
    private static final List<Integer> AUTHORED_EXIT_REGIONS =
            List.of(3, 4, 5, 6, 8, 10, 7, 9, 11, 13, 1);

    @Test
    void everyRoomMobCanFollowTheAuthoredTwoRopeExitToPortalTwo() {
        for (int mapId = 922_010_401; mapId <= 922_010_405; mapId++) {
            MapleMap map = AgentNavigationMapLoader.loadMapGeometry(mapId);
            Portal spawn = map.getPortal(0);
            Portal exit = map.getPortal(2);
            assertNotNull(spawn);
            assertNotNull(exit);
            assertEquals(922_010_400, exit.getTargetMapId());
            assertEquals(List.of(
                    new Rope(-534, -73, 213, false),
                    new Rope(-1_306, -73, 213, false)), map.getRopes());

            AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map);
            int startRegion = graph.findRegionId(map, spawn.getPosition());
            int targetRegion = graph.findRegionId(map, exit.getPosition());
            assertEquals(59, graph.version);
            assertEquals(AUTHORED_EXIT_REGIONS.getFirst(), startRegion);
            assertEquals(AUTHORED_EXIT_REGIONS.getLast(), targetRegion);
            assertTrue(AgentNavigationRouteOverlayPolicy.applies(graph, targetRegion));
            assertAuthoredSpine(graph, targetRegion, AUTHORED_EXIT_REGIONS, mapId);

            List<AgentNavigationGraph.Edge> spawnExit = AgentNavigationPathService.findPath(
                    graph, map, spawn.getPosition(), startRegion,
                    targetRegion, exit.getPosition(), "lpq-stage4-exit-preflight");
            assertEquals(AUTHORED_EXIT_REGIONS.subList(1, AUTHORED_EXIT_REGIONS.size()),
                    spawnExit.stream().map(edge -> edge.toRegionId).toList());

            Data mapData = DataProviderFactory.getDataProvider(WZFiles.MAP)
                    .getData("Map/Map9/" + mapId + ".img");
            Data life = mapData.getChildByPath("life");
            assertNotNull(life);
            int monsters = 0;
            for (Data entry : life) {
                if (!"m".equals(DataTool.getString("type", entry, ""))) continue;
                monsters++;
                Point position = new Point(DataTool.getInt("x", entry, 0),
                        DataTool.getInt("y", entry, 0));
                assertMergesOntoSpine(graph, map, position, targetRegion,
                        exit.getPosition(), AUTHORED_EXIT_REGIONS, mapId);
            }
            assertTrue(monsters >= 1, "no authored room monster in " + mapId);
        }
    }

    private static void assertAuthoredSpine(AgentNavigationGraph graph, int targetRegion,
                                             List<Integer> regions, int mapId) {
        for (int index = 0; index < regions.size() - 1; index++) {
            int from = regions.get(index);
            int to = regions.get(index + 1);
            List<AgentNavigationGraph.Edge> outgoing = graph.getOutgoing(from);
            assertTrue(outgoing.stream().anyMatch(edge -> edge.toRegionId == to),
                    () -> "missing authored edge " + from + "->" + to + " in " + mapId);
            for (AgentNavigationGraph.Edge edge : outgoing) {
                assertEquals(edge.toRegionId == to,
                        AgentNavigationRouteOverlayPolicy.allows(graph, targetRegion, edge));
            }
        }
    }

    private static void assertMergesOntoSpine(
            AgentNavigationGraph graph, MapleMap map, Point position, int targetRegion,
            Point exit, List<Integer> spine, int mapId) {
        int startRegion = graph.findRegionId(map, position);
        List<AgentNavigationGraph.Edge> path = AgentNavigationPathService.findPath(
                graph, map, position, startRegion, targetRegion, exit,
                "lpq-stage4-monster-exit-preflight");
        assertFalse(path.isEmpty(), () -> "monster cannot reach portal 2 in " + mapId);
        List<Integer> visited = new ArrayList<>();
        visited.add(startRegion);
        path.forEach(edge -> visited.add(edge.toRegionId));
        int mergeAt = -1;
        int spineAt = -1;
        for (int index = 0; index < visited.size(); index++) {
            int candidate = spine.indexOf(visited.get(index));
            if (candidate >= 0) {
                mergeAt = index;
                spineAt = candidate;
                break;
            }
        }
        assertTrue(mergeAt >= 0);
        assertEquals(spine.subList(spineAt, spine.size()),
                visited.subList(mergeAt, visited.size()));
    }
}
