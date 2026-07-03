package server.agents.capabilities.navigation;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;

import java.awt.Point;

/**
 * Agent-owned navigation target adjustment for grind-mode pathing.
 */
public final class AgentNavigationGrindTargetService {
    private AgentNavigationGrindTargetService() {
    }

    public static Point adjustPathTarget(boolean grinding,
                                         AgentNavigationGraph graph,
                                         int targetRegionId,
                                         Point rawTargetPos) {
        if (rawTargetPos == null || !grinding || targetRegionId < 0) {
            return rawTargetPos;
        }

        AgentNavigationGraph.Region targetRegion = graph.getRegion(targetRegionId);
        if (targetRegion == null || targetRegion.isRopeRegion) {
            return rawTargetPos;
        }

        int safeLeft = targetRegion.minX + AgentMovementPhysicsConfig.configuredGrindEdgeMargin();
        int safeRight = targetRegion.maxX - AgentMovementPhysicsConfig.configuredGrindEdgeMargin();
        if (safeLeft >= safeRight) {
            return rawTargetPos;
        }

        int clampedX = Math.max(safeLeft, Math.min(safeRight, rawTargetPos.x));
        return targetRegion.pointAt(clampedX);
    }
}
