package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.maps.MapleMap;
import server.maps.Foothold;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNavigationPathServiceTest {
    @Test
    void intraRegionTravelCostUsesWalkVelocityForGroundRegion() {
        AgentNavigationGraph graph = graphWithRegion(
                new AgentNavigationGraph.Region(1, List.of(new AgentNavigationGraph.Segment(
                        new Foothold(new Point(0, 100), new Point(100, 100), 1)))));

        int expected = Math.max(0, (int) Math.round((50 * 1000.0) / Math.max(1.0, graph.movementProfile.walkVelocityPxs())));

        assertEquals(expected, AgentNavigationPathService.intraRegionTravelCost(graph, 1, new Point(0, 100), new Point(50, 100)));
        assertEquals(expected, AgentNavigationPathService.heuristic(graph, new Point(0, 100), new Point(50, 100)));
    }

    @Test
    void intraRegionTravelCostUsesClimbSpeedForRopeRegion() {
        AgentNavigationGraph graph = graphWithRegion(new AgentNavigationGraph.Region(1, 100, 50, 250, false));
        int expected = Math.max(0, (int) Math.round((75 * 1000.0) / Math.max(1, AgentMovementPhysicsConfig.configuredClimbSpeedPxs())));

        assertEquals(expected, AgentNavigationPathService.intraRegionTravelCost(graph, 1, new Point(100, 50), new Point(100, 125)));
    }

    @Test
    void collapseLeadingWalkEdgesPromotesFirstActionableEdge() {
        AgentNavigationGraph.Edge collapsed = AgentNavigationPathService.collapseLeadingWalkEdges(List.of(
                edge(1, 2, AgentNavigationGraph.EdgeType.WALK, new Point(528, -914), new Point(528, -914), 50),
                edge(2, 3, AgentNavigationGraph.EdgeType.WALK, new Point(528, -914), new Point(528, -914), 50),
                edge(3, 4, AgentNavigationGraph.EdgeType.JUMP, new Point(540, -914), new Point(612, -980), 300)
        ));

        assertNotNull(collapsed);
        assertEquals(AgentNavigationGraph.EdgeType.JUMP, collapsed.type);
        assertEquals(1, collapsed.fromRegionId);
        assertEquals(4, collapsed.toRegionId);
        assertEquals(400, collapsed.cost);
    }

    @Test
    void collapseLeadingWalkEdgesReturnsNullWhenAllLeadingWalksConsumeNoMovement() {
        assertNull(AgentNavigationPathService.collapseLeadingWalkEdges(List.of(
                edge(1, 2, AgentNavigationGraph.EdgeType.WALK, new Point(10, 10), new Point(10, 10), 50),
                edge(2, 3, AgentNavigationGraph.EdgeType.WALK, new Point(10, 10), new Point(10, 10), 50)
        )));
    }

    @Test
    void preciseWalkTargetRequiresRealWalkMovement() {
        AgentNavigationGraph.Edge walkHandoff = edge(1, 2, AgentNavigationGraph.EdgeType.WALK,
                new Point(28, -1167), new Point(13, -1170), 100);
        AgentNavigationGraph.Edge noMoveWalk = edge(1, 2, AgentNavigationGraph.EdgeType.WALK,
                new Point(28, -1167), new Point(28, -1167), 50);

        assertTrue(AgentNavigationPathService.shouldUsePreciseWalkTarget(walkHandoff));
        assertFalse(AgentNavigationPathService.shouldUsePreciseWalkTarget(noMoveWalk));
        assertFalse(AgentNavigationPathService.shouldUsePreciseWalkTarget(null));
    }

    @Test
    void edgeUsabilityAllowsNonPortalEdgesAndRequiresOpenPortal() {
        MapleMap map = new MapleMap(910000001, 0, 0, 910000001, 1.0f);
        AgentNavigationGraph graph = graphWithRegion(new AgentNavigationGraph.Region(1, List.of(new AgentNavigationGraph.Segment(
                new Foothold(new Point(0, 100), new Point(100, 100), 1)))));

        assertTrue(AgentNavigationPathService.isEdgeUsable(graph, map,
                edge(1, 2, AgentNavigationGraph.EdgeType.WALK, new Point(0, 100), new Point(10, 100), 10)));
        assertFalse(AgentNavigationPathService.isEdgeUsable(graph, map,
                new AgentNavigationGraph.Edge(1, 2, AgentNavigationGraph.EdgeType.PORTAL,
                        new Point(0, 100), new Point(10, 100), 0, 0, 0, 42, 0, 10)));
    }

    private static AgentNavigationGraph graphWithRegion(AgentNavigationGraph.Region region) {
        return new AgentNavigationGraph(
                1,
                1,
                AgentMovementProfile.base(),
                List.of(region),
                Map.of(region.id, region),
                Map.of(),
                Map.of(),
                Set.of());
    }

    private static AgentNavigationGraph.Edge edge(int fromRegionId,
                                                  int toRegionId,
                                                  AgentNavigationGraph.EdgeType type,
                                                  Point start,
                                                  Point end,
                                                  int cost) {
        return new AgentNavigationGraph.Edge(fromRegionId, toRegionId, type,
                start, end, 0, 0, 0, 0, 0, cost);
    }
}
