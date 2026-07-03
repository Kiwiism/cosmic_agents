package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.maps.Foothold;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNavigationEdgeReadinessServiceTest {
    @Test
    void jumpRequiresTightXAndSingleJumpYThreshold() {
        AgentNavigationGraph.Edge jump = edge(AgentNavigationGraph.EdgeType.JUMP);
        int jumpY = AgentMovementPhysicsConfig.configuredJumpYThreshold();

        assertTrue(AgentNavigationEdgeReadinessService.isReadyForEdge(new Point(10, jumpY), jump));
        assertFalse(AgentNavigationEdgeReadinessService.isReadyForEdge(new Point(11, jumpY), jump));
        assertFalse(AgentNavigationEdgeReadinessService.isReadyForEdge(new Point(10, jumpY + 1), jump));
    }

    @Test
    void dropClimbAndPortalShareEdgeTolerance() {
        int jumpY = AgentMovementPhysicsConfig.configuredJumpYThreshold();

        for (AgentNavigationGraph.EdgeType type : new AgentNavigationGraph.EdgeType[]{
                AgentNavigationGraph.EdgeType.DROP,
                AgentNavigationGraph.EdgeType.CLIMB,
                AgentNavigationGraph.EdgeType.PORTAL}) {
            AgentNavigationGraph.Edge edge = edge(type);

            assertTrue(AgentNavigationEdgeReadinessService.isReadyForEdge(new Point(14, jumpY * 2), edge));
            assertFalse(AgentNavigationEdgeReadinessService.isReadyForEdge(new Point(15, jumpY * 2), edge));
            assertFalse(AgentNavigationEdgeReadinessService.isReadyForEdge(new Point(14, jumpY * 2 + 1), edge));
        }
    }

    @Test
    void walkUsesConfiguredStopDistancePlusLegacyPadding() {
        AgentNavigationGraph.Edge walk = edge(AgentNavigationGraph.EdgeType.WALK);
        int jumpY = AgentMovementPhysicsConfig.configuredJumpYThreshold();
        int readyX = AgentMovementPhysicsConfig.configuredStopDist() + 8;

        assertTrue(AgentNavigationEdgeReadinessService.isReadyForEdge(new Point(readyX, jumpY * 2), walk));
        assertFalse(AgentNavigationEdgeReadinessService.isReadyForEdge(new Point(readyX + 1, jumpY * 2), walk));
        assertFalse(AgentNavigationEdgeReadinessService.isReadyForEdge(new Point(readyX, jumpY * 2 + 1), walk));
    }

    @Test
    void jumpExecutionRequiresJumpEdgeAndLaunchWindow() {
        AgentNavigationGraph graph = graphWithGroundRegion(1, -100, 100, 0);
        AgentNavigationGraph.Edge jump = edge(AgentNavigationGraph.EdgeType.JUMP);
        AgentNavigationGraph.Edge drop = edge(AgentNavigationGraph.EdgeType.DROP);
        int jumpY = AgentMovementPhysicsConfig.configuredJumpYThreshold();

        assertTrue(AgentNavigationEdgeReadinessService.canExecuteJumpFromCurrentPosition(graph, new Point(0, jumpY), jump));
        assertFalse(AgentNavigationEdgeReadinessService.canExecuteJumpFromCurrentPosition(graph, new Point(0, jumpY + 1), jump));
        assertFalse(AgentNavigationEdgeReadinessService.canExecuteJumpFromCurrentPosition(graph, new Point(0, 0), drop));
    }

    @Test
    void selectedJumpExecutionRequiresSelectedLaunchXTolerance() {
        AgentNavigationGraph graph = graphWithGroundRegion(1, -100, 100, 0);
        AgentNavigationGraph.Edge jump = edge(AgentNavigationGraph.EdgeType.JUMP);
        int jumpY = AgentMovementPhysicsConfig.configuredJumpYThreshold();

        assertTrue(AgentNavigationEdgeReadinessService.canExecuteSelectedJumpFromCurrentPosition(
                graph, new Point(9, jumpY), jump, 10, 1));
        assertFalse(AgentNavigationEdgeReadinessService.canExecuteSelectedJumpFromCurrentPosition(
                graph, new Point(8, jumpY), jump, 10, 1));
        assertTrue(AgentNavigationEdgeReadinessService.canExecuteSelectedJumpFromCurrentPosition(
                graph, new Point(9, jumpY), jump, 10, 0));
        assertFalse(AgentNavigationEdgeReadinessService.canExecuteSelectedJumpFromCurrentPosition(
                graph, new Point(10, jumpY + 1), jump, 10, 10));
    }

    @Test
    void dropExecutionRequiresStraightDropEdgeAndLaunchWindow() {
        AgentNavigationGraph graph = graphWithGroundRegion(1, -100, 100, 0);
        AgentNavigationGraph.Edge drop = edge(AgentNavigationGraph.EdgeType.DROP);
        AgentNavigationGraph.Edge directionalDrop = new AgentNavigationGraph.Edge(1, 2, AgentNavigationGraph.EdgeType.DROP,
                new Point(0, 0), new Point(100, 0),
                -20, 20, 1, 1, 0, 0, 0, 100);
        AgentNavigationGraph.Edge jump = edge(AgentNavigationGraph.EdgeType.JUMP);
        int jumpY = AgentMovementPhysicsConfig.configuredJumpYThreshold();

        assertTrue(AgentNavigationEdgeReadinessService.canExecuteDropFromCurrentPosition(graph, new Point(0, jumpY), drop));
        assertFalse(AgentNavigationEdgeReadinessService.canExecuteDropFromCurrentPosition(graph, new Point(0, jumpY + 1), drop));
        assertFalse(AgentNavigationEdgeReadinessService.canExecuteDropFromCurrentPosition(graph, new Point(0, 0), directionalDrop));
        assertFalse(AgentNavigationEdgeReadinessService.canExecuteDropFromCurrentPosition(graph, new Point(0, 0), jump));
    }

    private static AgentNavigationGraph.Edge edge(AgentNavigationGraph.EdgeType type) {
        return new AgentNavigationGraph.Edge(1, 2, type,
                new Point(0, 0), new Point(100, 0),
                -20, 20, 0, 1, 0, 0, 0, 100);
    }

    private static AgentNavigationGraph graphWithGroundRegion(int regionId, int x1, int x2, int y) {
        AgentNavigationGraph.Region ground = new AgentNavigationGraph.Region(regionId, List.of(
                new AgentNavigationGraph.Segment(new Foothold(new Point(x1, y), new Point(x2, y), regionId))));
        return new AgentNavigationGraph(100,
                1,
                List.of(ground),
                Map.of(regionId, ground),
                Map.of(),
                Map.of(),
                Set.of());
    }
}
