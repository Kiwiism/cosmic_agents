package server.agents.capabilities.movement;

import java.awt.Point;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.navigation.AgentNavigationGraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentGroundMovementPolicyTest {
    @Test
    void shouldStopInsideStopDistance() {
        assertEquals(0, AgentGroundMovementPolicy.calcStepX(0, 20, true, 25, 80, 12));
        assertEquals(0, AgentGroundMovementPolicy.calcStepX(0, -20, true, 25, 80, 12));
    }

    @Test
    void shouldHoldUntilFollowDistanceWhenNotAlreadyMoving() {
        assertEquals(0, AgentGroundMovementPolicy.calcStepX(0, 80, false, 25, 80, 12));
        assertEquals(12, AgentGroundMovementPolicy.calcStepX(0, 81, false, 25, 80, 12));
    }

    @Test
    void shouldKeepMovingInsideFollowDistanceWhenAlreadyMoving() {
        assertEquals(12, AgentGroundMovementPolicy.calcStepX(0, 80, true, 25, 80, 12));
        assertEquals(-12, AgentGroundMovementPolicy.calcStepX(0, -80, true, 25, 80, 12));
    }

    @Test
    void shouldClampStepToRemainingDistance() {
        assertEquals(31, AgentGroundMovementPolicy.calcStepX(0, 31, true, 25, 80, 40));
        assertEquals(-31, AgentGroundMovementPolicy.calcStepX(0, -31, true, 25, 80, 40));
    }

    @Test
    void shouldUseFullWalkStepForDistantTargets() {
        assertEquals(12, AgentGroundMovementPolicy.calcStepX(0, 200, false, 25, 80, 12));
        assertEquals(-12, AgentGroundMovementPolicy.calcStepX(0, -200, false, 25, 80, 12));
    }

    @Test
    void preciseStopDistanceMatchesLegacyNavigationEdges() {
        AgentNavigationGraph.Edge climbEdge = edge(AgentNavigationGraph.EdgeType.CLIMB, 0);
        AgentNavigationGraph.Edge jumpEdge = edge(AgentNavigationGraph.EdgeType.JUMP, 1);
        AgentNavigationGraph.Edge downJumpEdge = edge(AgentNavigationGraph.EdgeType.DROP, 0);
        AgentNavigationGraph.Edge walkEdge = edge(AgentNavigationGraph.EdgeType.WALK, 0);

        assertEquals(1, AgentGroundMovementPolicy.preciseNavStopDist(climbEdge));
        assertEquals(0, AgentGroundMovementPolicy.preciseNavStopDist(jumpEdge));
        assertEquals(0, AgentGroundMovementPolicy.preciseNavStopDist(downJumpEdge));
        assertEquals(4, AgentGroundMovementPolicy.preciseNavStopDist(walkEdge));
        assertEquals(4, AgentGroundMovementPolicy.preciseNavStopDist(null));
    }

    @Test
    void directionalDropRequiresDropEdgeWithLaunchDirection() {
        assertTrue(AgentGroundMovementPolicy.isDirectionalDropEdge(edge(AgentNavigationGraph.EdgeType.DROP, -1)));
        assertTrue(AgentGroundMovementPolicy.isDirectionalDropEdge(edge(AgentNavigationGraph.EdgeType.DROP, 1)));
        assertFalse(AgentGroundMovementPolicy.isDirectionalDropEdge(edge(AgentNavigationGraph.EdgeType.DROP, 0)));
        assertFalse(AgentGroundMovementPolicy.isDirectionalDropEdge(edge(AgentNavigationGraph.EdgeType.JUMP, 1)));
        assertFalse(AgentGroundMovementPolicy.isDirectionalDropEdge(null));
    }

    private static AgentNavigationGraph.Edge edge(AgentNavigationGraph.EdgeType type, int launchStepX) {
        return new AgentNavigationGraph.Edge(1, 2, type, new Point(10, 20), new Point(30, 40),
                launchStepX, 0, 0, 0, 0, 0);
    }
}
