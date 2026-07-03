package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.maps.Foothold;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
