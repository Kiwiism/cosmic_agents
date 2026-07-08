package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import client.Character;
import server.agents.integration.AgentClimbStateRuntime;
import server.agents.integration.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentNavigationWaypointServiceTest {
    @Test
    void jumpWaypointClampsToLaunchWindowOnGroundRegion() {
        AgentNavigationGraph graph = graphWithGroundRegion(1, 500, 550, 107);
        AgentNavigationGraph.Edge jump = edge(AgentNavigationGraph.EdgeType.JUMP,
                new Point(516, 107), new Point(620, 20), 516, 523, 0);

        assertEquals(new Point(516, 107),
                AgentNavigationWaypointService.selectJumpWaypoint(graph, new Point(449, 113), jump));
        assertEquals(new Point(523, 107),
                AgentNavigationWaypointService.selectJumpWaypoint(graph, new Point(540, 113), jump));
        assertEquals(new Point(520, 107),
                AgentNavigationWaypointService.selectJumpWaypoint(graph, new Point(520, 113), jump));
    }

    @Test
    void jumpWaypointFallsBackToEdgeStartWhenRegionMissingOrRope() {
        AgentNavigationGraph.Edge jump = edge(AgentNavigationGraph.EdgeType.JUMP,
                new Point(516, 107), new Point(620, 20), 516, 523, 0);

        assertEquals(new Point(516, 107),
                AgentNavigationWaypointService.selectJumpWaypoint(graphWithGroundRegion(99, 500, 550, 107),
                        new Point(520, 113), jump));
        assertEquals(new Point(516, 107),
                AgentNavigationWaypointService.selectJumpWaypoint(graphWithRopeRegion(1),
                        new Point(520, 113), jump));
    }

    @Test
    void straightDropWaypointClampsToLaunchWindowOrFallsBackToStart() {
        AgentNavigationGraph graph = graphWithGroundRegion(1, 100, 160, 40);
        AgentNavigationGraph.Edge drop = edge(AgentNavigationGraph.EdgeType.DROP,
                new Point(120, 40), new Point(120, 160), 110, 130, 0);

        assertEquals(new Point(110, 40),
                AgentNavigationWaypointService.selectStraightDropWaypoint(graph, new Point(90, 40), drop));
        assertEquals(new Point(130, 40),
                AgentNavigationWaypointService.selectStraightDropWaypoint(graph, new Point(150, 40), drop));
        assertEquals(new Point(125, 40),
                AgentNavigationWaypointService.selectStraightDropWaypoint(graph, new Point(125, 40), drop));
        assertEquals(new Point(120, 40),
                AgentNavigationWaypointService.selectStraightDropWaypoint(null, new Point(125, 40), drop));
        assertEquals(new Point(120, 40),
                AgentNavigationWaypointService.selectStraightDropWaypoint(graphWithRopeRegion(1), new Point(125, 40), drop));
    }

    @Test
    void entryBackedJumpLaunchSelectionCachesStableLaunchX() {
        MapleMap map = new MapleMap(910000010, 0, 0, 910000010, 1.0f);
        AgentNavigationGraph graph = graphWithGroundRegion(1, 500, 530, 107);
        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentNavigationGraph.Edge jump = edge(AgentNavigationGraph.EdgeType.JUMP,
                new Point(520, 107), new Point(480, 36), 516, 523, -8);

        int first = AgentNavigationWaypointService.selectJumpLaunchX(entry, graph, jump);
        int second = AgentNavigationWaypointService.selectJumpLaunchX(entry, graph, jump);

        assertTrue(first >= 519 && first <= 520);
        assertEquals(first, second);
    }

    @Test
    void climbWaypointUsesEndPointWhenAirborne() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentMovementStateRuntime.setInAir(entry, true);
        AgentNavigationGraph.Edge edge = edge(AgentNavigationGraph.EdgeType.CLIMB,
                new Point(100, 200), new Point(100, 120), 0, 0, 0);

        assertEquals(new Point(100, 120),
                AgentNavigationWaypointService.selectClimbWaypoint(null, entry, new Point(100, 190), edge,
                        (graph, e, botPos, climbEdge) -> false));
    }

    @Test
    void climbWaypointHoldsPositionOnlyWhenJumpExitReady() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentClimbStateRuntime.setClimbingOnRope(entry, new Rope(100, 80, 220, false));
        AgentNavigationGraph graph = graphWithRopeRegion(1);
        AgentNavigationGraph.Edge jumpExit = edge(AgentNavigationGraph.EdgeType.CLIMB,
                new Point(100, 120), new Point(150, 80), 0, 0, 4);
        Point botPos = new Point(100, 120);

        assertEquals(botPos,
                AgentNavigationWaypointService.selectClimbWaypoint(graph, entry, botPos, jumpExit,
                        (g, e, p, climbEdge) -> true));
        assertEquals(new Point(100, 120),
                AgentNavigationWaypointService.selectClimbWaypoint(graph, entry, new Point(100, 130), jumpExit,
                        (g, e, p, climbEdge) -> false));
    }

    @Test
    void climbWaypointKeepsRopeXForZeroStepExitWhileClimbing() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Rope rope = new Rope(300, 100, 240, false);
        AgentClimbStateRuntime.setClimbingOnRope(entry, rope);
        AgentNavigationGraph.Edge edge = edge(AgentNavigationGraph.EdgeType.CLIMB,
                new Point(300, 100), new Point(260, 90), 0, 0, 0);

        assertEquals(new Point(300, 90),
                AgentNavigationWaypointService.selectClimbWaypoint(null, entry, new Point(300, 110), edge,
                        (graph, e, botPos, climbEdge) -> false));
    }

    @Test
    void climbWaypointUsesStartPointWhenNotClimbing() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentNavigationGraph.Edge edge = edge(AgentNavigationGraph.EdgeType.CLIMB,
                new Point(300, 100), new Point(260, 90), 0, 0, 0);

        assertEquals(new Point(300, 100),
                AgentNavigationWaypointService.selectClimbWaypoint(null, entry, new Point(300, 110), edge,
                        (graph, e, botPos, climbEdge) -> false));
    }

    private static AgentNavigationGraph graphWithGroundRegion(int regionId, int x1, int x2, int y) {
        AgentNavigationGraph.Region ground = new AgentNavigationGraph.Region(regionId, List.of(
                new AgentNavigationGraph.Segment(new Foothold(new Point(x1, y), new Point(x2, y), regionId))));
        return graph(regionId, ground);
    }

    private static AgentNavigationGraph graphWithRopeRegion(int regionId) {
        return graph(regionId, new AgentNavigationGraph.Region(regionId, 120, 0, 100, false));
    }

    private static AgentNavigationGraph graph(int regionId, AgentNavigationGraph.Region region) {
        return new AgentNavigationGraph(100,
                1,
                List.of(region),
                Map.of(regionId, region),
                Map.of(),
                Map.of(),
                Set.of());
    }

    private static AgentNavigationGraph.Edge edge(AgentNavigationGraph.EdgeType type,
                                                  Point start,
                                                  Point end,
                                                  int launchMinX,
                                                  int launchMaxX,
                                                  int launchStepX) {
        return new AgentNavigationGraph.Edge(1, 2, type,
                start, end, launchMinX, launchMaxX, launchStepX, 0, 0, 0, 0, 100);
    }
}
