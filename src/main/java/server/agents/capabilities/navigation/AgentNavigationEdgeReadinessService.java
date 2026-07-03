package server.agents.capabilities.navigation;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;

import java.awt.Point;

/**
 * Agent-owned policy for deciding when the character is close enough to execute
 * the next navigation edge.
 */
public final class AgentNavigationEdgeReadinessService {
    private static final int JUMP_READY_X_TOLERANCE = 10;
    private static final int EDGE_READY_X_TOLERANCE = 14;

    private AgentNavigationEdgeReadinessService() {
    }

    public static boolean isReadyForEdge(Point botPos, AgentNavigationGraph.Edge edge) {
        int dx = Math.abs(botPos.x - edge.startPoint.x);
        int dy = Math.abs(botPos.y - edge.startPoint.y);

        return switch (edge.type) {
            case JUMP -> dx <= JUMP_READY_X_TOLERANCE
                    && dy <= AgentMovementPhysicsConfig.configuredJumpYThreshold();
            case DROP, CLIMB, PORTAL -> dx <= EDGE_READY_X_TOLERANCE
                    && dy <= AgentMovementPhysicsConfig.configuredJumpYThreshold() * 2;
            default -> dx <= AgentMovementPhysicsConfig.configuredStopDist() + 8
                    && dy <= AgentMovementPhysicsConfig.configuredJumpYThreshold() * 2;
        };
    }
}
