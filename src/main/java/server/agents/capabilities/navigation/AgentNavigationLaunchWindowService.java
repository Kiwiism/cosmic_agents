package server.agents.capabilities.navigation;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;

import java.awt.Point;

/**
 * Agent-owned launch-window checks for navigation jump and drop edges.
 */
public final class AgentNavigationLaunchWindowService {
    private AgentNavigationLaunchWindowService() {
    }

    public static boolean isWithinJumpLaunchWindow(AgentNavigationGraph graph,
                                                   Point botPos,
                                                   AgentNavigationGraph.Edge edge) {
        if (botPos == null || edge.type != AgentNavigationGraph.EdgeType.JUMP || !edge.containsLaunchX(botPos.x)) {
            return false;
        }

        AgentNavigationGraph.Region fromRegion = graph.getRegion(edge.fromRegionId);
        if (fromRegion == null) {
            return false;
        }

        Point expectedLaunchPoint = fromRegion.pointAt(botPos.x);
        return Math.abs(botPos.y - expectedLaunchPoint.y) <= AgentMovementPhysicsConfig.configuredJumpYThreshold();
    }

    public static boolean isWithinDropLaunchWindow(AgentNavigationGraph graph,
                                                   Point botPos,
                                                   AgentNavigationGraph.Edge edge) {
        if (botPos == null
                || edge.type != AgentNavigationGraph.EdgeType.DROP
                || edge.launchStepX != 0
                || !edge.containsLaunchX(botPos.x)) {
            return false;
        }

        if (graph == null) {
            return Math.abs(botPos.y - edge.startPoint.y) <= AgentMovementPhysicsConfig.configuredJumpYThreshold();
        }

        AgentNavigationGraph.Region fromRegion = graph.getRegion(edge.fromRegionId);
        if (fromRegion == null || fromRegion.isRopeRegion) {
            return false;
        }

        Point expectedLaunchPoint = fromRegion.pointAt(botPos.x);
        return Math.abs(botPos.y - expectedLaunchPoint.y) <= AgentMovementPhysicsConfig.configuredJumpYThreshold();
    }
}
