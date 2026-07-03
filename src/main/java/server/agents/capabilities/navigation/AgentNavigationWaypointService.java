package server.agents.capabilities.navigation;

import java.awt.Point;

/**
 * Agent-owned waypoint selection for navigation edges that do not require live
 * runtime state.
 */
public final class AgentNavigationWaypointService {
    private AgentNavigationWaypointService() {
    }

    public static Point selectJumpWaypoint(AgentNavigationGraph graph,
                                           Point botPos,
                                           AgentNavigationGraph.Edge edge) {
        AgentNavigationGraph.Region fromRegion = graph.getRegion(edge.fromRegionId);
        if (fromRegion == null || fromRegion.isRopeRegion) {
            return new Point(edge.startPoint);
        }
        int targetX = edge.containsLaunchX(botPos.x)
                ? botPos.x
                : botPos.x < edge.launchMinX ? edge.launchMinX : edge.launchMaxX;
        return fromRegion.pointAt(targetX);
    }

    public static Point selectStraightDropWaypoint(AgentNavigationGraph graph,
                                                   Point botPos,
                                                   AgentNavigationGraph.Edge edge) {
        AgentNavigationGraph.Region fromRegion = graph != null ? graph.getRegion(edge.fromRegionId) : null;
        if (fromRegion == null || fromRegion.isRopeRegion) {
            return new Point(edge.startPoint);
        }
        int targetX = edge.containsLaunchX(botPos.x)
                ? botPos.x
                : botPos.x < edge.launchMinX ? edge.launchMinX : edge.launchMaxX;
        return fromRegion.pointAt(targetX);
    }
}
