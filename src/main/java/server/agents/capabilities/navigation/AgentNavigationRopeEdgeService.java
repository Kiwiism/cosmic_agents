package server.agents.capabilities.navigation;

import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.maps.Rope;

import java.awt.Point;

/**
 * Agent-owned rope and climb-edge predicates used by navigation execution.
 */
public final class AgentNavigationRopeEdgeService {
    private AgentNavigationRopeEdgeService() {
    }

    public static boolean canGrabRopeAtCurrentPosition(Point botPos, Rope rope) {
        return Math.abs(botPos.x - rope.x()) <= AgentMovementPhysicsConfig.configuredRopeGrabX()
                && botPos.y >= AgentNavigationPhysicsService.firstClimbableY(rope)
                && botPos.y <= rope.bottomY();
    }

    public static boolean canAttachToRopeFromTopPlatform(AgentNavigationGraph.Edge edge, Point botPos, Rope rope) {
        return Math.abs(botPos.x - rope.x()) <= AgentMovementPhysicsConfig.configuredRopeGrabX()
                && edge.endPoint.y == AgentNavigationPhysicsService.firstClimbableY(rope)
                && botPos.y < rope.topY()
                && rope.topY() - botPos.y <= AgentMovementPhysicsConfig.configuredMaxSnapDrop();
    }

    public static boolean canGrabRopeFromTopPlatform(AgentNavigationGraph.Edge edge, Point botPos, Rope rope) {
        return edge.startPoint.y <= rope.topY() + AgentMovementPhysicsConfig.configuredJumpYThreshold()
                && Math.abs(botPos.x - rope.x()) <= AgentMovementPhysicsConfig.configuredRopeGrabX();
    }

    public static boolean canExecuteClimbEntryFromCurrentPosition(Point botPos,
                                                                  AgentNavigationGraph.Edge edge,
                                                                  Rope rope) {
        return rope != null && (canGrabRopeAtCurrentPosition(botPos, rope)
                || canAttachToRopeFromTopPlatform(edge, botPos, rope)
                || canGrabRopeFromTopPlatform(edge, botPos, rope)
                || canExecuteGroundRopeJumpEntryFromCurrentPosition(botPos, edge));
    }

    public static boolean canExecuteGroundRopeJumpEntryFromCurrentPosition(Point botPos,
                                                                          AgentNavigationGraph.Edge edge) {
        if (botPos == null || edge == null || edge.type != AgentNavigationGraph.EdgeType.CLIMB) {
            return false;
        }
        return edge.containsLaunchX(botPos.x)
                && Math.abs(botPos.y - edge.startPoint.y) <= AgentMovementPhysicsConfig.configuredJumpYThreshold() * 2;
    }

    public static boolean isRopeEntryEdge(AgentNavigationGraph graph, AgentNavigationGraph.Edge edge) {
        if (edge.type != AgentNavigationGraph.EdgeType.CLIMB) {
            return false;
        }

        AgentNavigationGraph.Region from = graph.getRegion(edge.fromRegionId);
        AgentNavigationGraph.Region to = graph.getRegion(edge.toRegionId);
        return from != null && to != null && !from.isRopeRegion && to.isRopeRegion;
    }

    public static boolean isTopStepOffExit(Rope rope, Point botPos, AgentNavigationGraph.Edge edge) {
        if (rope == null || botPos == null || edge == null || edge.launchStepX != 0) {
            return false;
        }
        return edge.startPoint.y == rope.topY()
                && Math.abs(edge.endPoint.y - rope.topY()) <= AgentMovementPhysicsConfig.configuredJumpYThreshold() * 2
                && botPos.y <= rope.topY() + AgentMovementPhysicsConfig.configuredJumpYThreshold() * 2;
    }

    public static boolean isTopRopeJumpExitReady(Rope rope, Point botPos, AgentNavigationGraph.Edge edge) {
        if (rope == null || botPos == null || edge == null || edge.launchStepX == 0) {
            return false;
        }
        int firstClimbableY = AgentNavigationPhysicsService.firstClimbableY(rope);
        return edge.startPoint.x == rope.x()
                && edge.startPoint.y == firstClimbableY
                && botPos.x == rope.x()
                && botPos.y >= firstClimbableY
                && botPos.y <= firstClimbableY + AgentMovementKinematicsService.climbStepPerTick() + 2;
    }
}
