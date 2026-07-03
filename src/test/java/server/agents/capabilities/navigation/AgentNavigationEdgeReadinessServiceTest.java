package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;

import java.awt.Point;

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

    private static AgentNavigationGraph.Edge edge(AgentNavigationGraph.EdgeType type) {
        return new AgentNavigationGraph.Edge(1, 2, type,
                new Point(0, 0), new Point(100, 0),
                -20, 20, 0, 1, 0, 0, 0, 100);
    }
}
