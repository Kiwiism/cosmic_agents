package server.agents.capabilities.navigation;

import java.awt.Point;

/**
 * Agent-owned policy for deciding when the character is close enough to execute
 * the next navigation edge.
 */
public final class AgentNavigationEdgeReadinessService {
    private static final int JUMP_READY_X_TOLERANCE = config.AgentTuning.intValue("server.agents.capabilities.navigation.AgentNavigationEdgeReadinessService.JUMP_READY_X_TOLERANCE");
    private static final int EDGE_READY_X_TOLERANCE = config.AgentTuning.intValue("server.agents.capabilities.navigation.AgentNavigationEdgeReadinessService.EDGE_READY_X_TOLERANCE");

    private AgentNavigationEdgeReadinessService() {
    }

    public static boolean isReadyForEdge(Point botPos, AgentNavigationGraph.Edge edge) {
        int dx = Math.abs(botPos.x - edge.startPoint.x);
        int dy = Math.abs(botPos.y - edge.startPoint.y);

        return switch (edge.type) {
            case JUMP -> dx <= JUMP_READY_X_TOLERANCE
                    && dy <= AgentNavigationPhysicsService.jumpYThreshold();
            case DROP, CLIMB, PORTAL -> dx <= EDGE_READY_X_TOLERANCE
                    && dy <= AgentNavigationPhysicsService.jumpYThreshold() * 2;
            default -> dx <= AgentNavigationPhysicsService.stopDistance() + 8
                    && dy <= AgentNavigationPhysicsService.jumpYThreshold() * 2;
        };
    }

    public static boolean canExecuteJumpFromCurrentPosition(AgentNavigationGraph graph,
                                                            Point botPos,
                                                            AgentNavigationGraph.Edge edge) {
        if (edge.type != AgentNavigationGraph.EdgeType.JUMP) {
            return false;
        }
        return AgentNavigationLaunchWindowService.isWithinJumpLaunchWindow(graph, botPos, edge);
    }

    public static boolean canExecuteSelectedJumpFromCurrentPosition(AgentNavigationGraph graph,
                                                                    Point botPos,
                                                                    AgentNavigationGraph.Edge edge,
                                                                    int selectedLaunchX,
                                                                    int tolerance) {
        if (edge.type != AgentNavigationGraph.EdgeType.JUMP
                || !AgentNavigationLaunchWindowService.isWithinJumpLaunchWindow(
                        graph, botPos, edge, Math.max(1, tolerance))) {
            return false;
        }
        return Math.abs(botPos.x - selectedLaunchX) <= Math.max(1, tolerance);
    }

    public static boolean canExecuteDropFromCurrentPosition(AgentNavigationGraph graph,
                                                            Point botPos,
                                                            AgentNavigationGraph.Edge edge) {
        if (edge.type != AgentNavigationGraph.EdgeType.DROP) {
            return false;
        }
        if (edge.launchStepX != 0) {
            return false;
        }
        return AgentNavigationLaunchWindowService.isWithinDropLaunchWindow(graph, botPos, edge);
    }
}
