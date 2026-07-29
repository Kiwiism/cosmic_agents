package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMapGraphServiceTest {
    @Test
    void graphViewCollapsesParallelEdgesAndIncludesProfiles() {
        MapleMap map = emptyMap(103000000, "Kerning City");
        AgentNavigationGraph.Edge slow = edge(1, 2, AgentNavigationGraph.EdgeType.JUMP, 300);
        AgentNavigationGraph.Edge fast = edge(1, 2, AgentNavigationGraph.EdgeType.JUMP, 200);
        AgentNavigationGraph graph = graph(Map.of(1, List.of(slow, fast)));

        AgentMapGraphService.MapGraphView view = AgentMapGraphService.graphView(
                map,
                graph,
                List.of(AgentMovementProfile.base(), new AgentMovementProfile(120, 110)));

        assertEquals(103000000, view.mapId());
        assertEquals("Kerning City", view.name());
        assertEquals(2, view.regions().size());
        assertEquals(1, view.edges().size());
        assertEquals(2, view.edges().getFirst().parallelCount());
        assertEquals(200, view.edges().getFirst().cost());
        assertEquals(2, view.profiles().size());
        assertFalse(view.regions().getFirst().report().isEmpty());
    }

    @Test
    void routeTestRunsNormalAndExhaustiveSearchWithoutMovingCharacters() {
        MapleMap map = emptyMap(103000000, "Kerning City");
        AgentNavigationGraph graph = graph(Map.of(
                1, List.of(edge(1, 2, AgentNavigationGraph.EdgeType.WALK, 100))));

        AgentMapGraphService.RouteView normal =
                AgentMapGraphService.testRoute(map, graph, 1, 2, false);
        AgentMapGraphService.RouteView exhaustive =
                AgentMapGraphService.testRoute(map, graph, 1, 2, true);

        assertTrue(normal.reached());
        assertEquals("normal", normal.mode());
        assertEquals(1, normal.path().size());
        assertTrue(exhaustive.reached());
        assertEquals("exhaustive", exhaustive.mode());
        assertEquals(normal.cost(), exhaustive.cost());
    }

    private static MapleMap emptyMap(int id, String name) {
        MapleMap map = mock(MapleMap.class);
        when(map.getId()).thenReturn(id);
        when(map.getMapName()).thenReturn(name);
        when(map.getMapObjectsInRange(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(map.getPortals()).thenReturn(List.of());
        when(map.getAllPlayers()).thenReturn(List.of());
        return map;
    }

    private static AgentNavigationGraph graph(Map<Integer, List<AgentNavigationGraph.Edge>> outgoing) {
        AgentNavigationGraph.Region first = region(1, 0, 100);
        AgentNavigationGraph.Region second = region(2, 100, 200);
        return new AgentNavigationGraph(
                103000000,
                55,
                AgentMovementProfile.base(),
                List.of(first, second),
                Map.of(1, first, 2, second),
                Map.of(1, 1, 2, 2),
                outgoing,
                Set.of());
    }

    private static AgentNavigationGraph.Region region(int id, int x1, int x2) {
        return new AgentNavigationGraph.Region(id, List.of(new AgentNavigationGraph.Segment(
                new Foothold(new Point(x1, 100), new Point(x2, 100), id))));
    }

    private static AgentNavigationGraph.Edge edge(int from,
                                                   int to,
                                                   AgentNavigationGraph.EdgeType type,
                                                   int cost) {
        return new AgentNavigationGraph.Edge(
                from,
                to,
                type,
                new Point((from - 1) * 100, 100),
                new Point((to - 1) * 100, 100),
                0,
                0,
                0,
                0,
                0,
                cost);
    }
}
