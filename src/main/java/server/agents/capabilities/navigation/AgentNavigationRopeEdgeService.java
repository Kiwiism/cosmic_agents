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

    @FunctionalInterface
    public interface RopeResolver {
        Rope findRope(AgentNavigationGraph.Region region);
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

    public static boolean canExecuteClimbExitFromCurrentPosition(AgentNavigationGraph graph,
                                                                 Point botPos,
                                                                 AgentNavigationGraph.Edge edge,
                                                                 RopeResolver ropeResolver) {
        if (edge.type != AgentNavigationGraph.EdgeType.CLIMB) {
            return false;
        }

        if (edge.launchStepX != 0 && botPos.y != edge.startPoint.y) {
            Rope rope = ropeResolver.findRope(graph.getRegion(edge.fromRegionId));
            if (!isTopRopeJumpExitReady(rope, botPos, edge)) {
                // Rope-exit jump edges are authored from a specific climb height. Launching from
                // any other Y changes the ballistic arc; climb movement reaches the authored
                // first climbable pixel before this executes.
                return false;
            }
        }

        AgentNavigationGraph.Region toRegion = graph.getRegion(edge.toRegionId);
        if (toRegion != null && toRegion.isRopeRegion) {
            return Math.abs(botPos.y - edge.startPoint.y) <= AgentMovementPhysicsConfig.configuredJumpYThreshold() * 2;
        }

        if (edge.launchStepX == 0) {
            Rope rope = ropeResolver.findRope(graph.getRegion(edge.fromRegionId));
            return rope != null && isTopStepOffExit(rope, botPos, edge);
        }

        return Math.abs(botPos.y - edge.startPoint.y) <= AgentMovementPhysicsConfig.configuredJumpYThreshold() * 2;
    }
}
