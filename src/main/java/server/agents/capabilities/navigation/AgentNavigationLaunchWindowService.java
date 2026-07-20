package server.agents.capabilities.navigation;

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
        return isWithinJumpLaunchWindow(graph, botPos, edge, 0);
    }

    public static boolean isWithinJumpLaunchWindow(AgentNavigationGraph graph,
                                                   Point botPos,
                                                   AgentNavigationGraph.Edge edge,
                                                   int minimumAcceptanceSpanPx) {
        if (botPos == null || edge.type != AgentNavigationGraph.EdgeType.JUMP) {
            return false;
        }
        int tolerance = Math.max(0,
                (minimumAcceptanceSpanPx - (edge.launchMaxX - edge.launchMinX) + 1) / 2);
        if (!edge.containsLaunchX(botPos.x, tolerance)) {
            return false;
        }

        AgentNavigationGraph.Region fromRegion = graph.getRegion(edge.fromRegionId);
        if (fromRegion == null) {
            return false;
        }

        Point expectedLaunchPoint = fromRegion.pointAt(botPos.x);
        return Math.abs(botPos.y - expectedLaunchPoint.y) <= AgentNavigationPhysicsService.jumpYThreshold();
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
            return Math.abs(botPos.y - edge.startPoint.y) <= AgentNavigationPhysicsService.jumpYThreshold();
        }

        AgentNavigationGraph.Region fromRegion = graph.getRegion(edge.fromRegionId);
        if (fromRegion == null || fromRegion.isRopeRegion) {
            return false;
        }

        Point expectedLaunchPoint = fromRegion.pointAt(botPos.x);
        return Math.abs(botPos.y - expectedLaunchPoint.y) <= AgentNavigationPhysicsService.jumpYThreshold();
    }

    public static boolean hasReachedDirectionalDropRunway(Point botPos, AgentNavigationGraph.Edge edge) {
        if (botPos == null || edge == null || edge.launchStepX == 0) {
            return false;
        }

        int direction = Integer.signum(edge.launchStepX);
        return direction > 0
                ? botPos.x >= edge.startPoint.x
                : botPos.x <= edge.startPoint.x;
    }
}
