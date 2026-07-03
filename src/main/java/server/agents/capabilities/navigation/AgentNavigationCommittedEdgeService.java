package server.agents.capabilities.navigation;

import client.Character;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;

/**
 * Agent-owned committed-edge comparison and retention policy.
 */
public final class AgentNavigationCommittedEdgeService {
    private AgentNavigationCommittedEdgeService() {
    }

    @FunctionalInterface
    public interface NextEdgeFinder {
        AgentNavigationGraph.Edge findNextEdge(AgentNavigationGraph graph,
                                               Character bot,
                                               int startRegionId,
                                               int targetRegionId,
                                               Point targetPos);
    }

    public static AgentNavigationGraph.Edge refreshCommittedGroundEdge(AgentNavigationGraph graph,
                                                                       BotEntry entry,
                                                                       Character bot,
                                                                       int startRegionId,
                                                                       int targetRegionId,
                                                                       Point targetPos,
                                                                       AgentNavigationGraph.Edge edge,
                                                                       boolean runAiTick,
                                                                       NextEdgeFinder nextEdgeFinder) {
        if (!runAiTick
                || edge == null
                || AgentBotMovementStateRuntime.inAir(entry)
                || AgentBotClimbStateRuntime.climbing(entry)
                || startRegionId < 0
                || targetRegionId < 0
                || startRegionId == targetRegionId) {
            return edge;
        }

        AgentNavigationGraph.Edge bestEdge = nextEdgeFinder.findNextEdge(graph, bot, startRegionId, targetRegionId, targetPos);
        if (bestEdge == null || sameEdge(edge, bestEdge)) {
            return edge;
        }
        if (shouldRetainCommittedGroundEdge(edge, bestEdge)) {
            return edge;
        }

        AgentBotNavigationDebugStateRuntime.setActiveNavigationEdge(entry, bestEdge);
        AgentBotNavigationDebugStateRuntime.setNavTargetRegionId(entry, targetRegionId);
        AgentBotNavigationDebugStateRuntime.clearNavTargetPosition(entry);
        AgentBotNavigationDebugStateRuntime.setNavPreciseTarget(entry, false);
        return bestEdge;
    }

    public static boolean sameEdge(AgentNavigationGraph.Edge left, AgentNavigationGraph.Edge right) {
        return left == right || (left != null
                && right != null
                && left.fromRegionId == right.fromRegionId
                && left.toRegionId == right.toRegionId
                && left.type == right.type
                && left.launchMinX == right.launchMinX
                && left.launchMaxX == right.launchMaxX
                && left.launchStepX == right.launchStepX
                && left.portalId == right.portalId
                && left.ropeX == right.ropeX
                && left.ropeTopY == right.ropeTopY
                && left.ropeBottomY == right.ropeBottomY
                && left.startPoint.equals(right.startPoint)
                && left.endPoint.equals(right.endPoint));
    }

    public static boolean shouldRetainCommittedGroundEdge(AgentNavigationGraph.Edge current,
                                                          AgentNavigationGraph.Edge replacement) {
        if (current == null || replacement == null) {
            return false;
        }
        if (current.fromRegionId != replacement.fromRegionId
                || current.toRegionId != replacement.toRegionId) {
            return false;
        }
        // Equivalent first exits into the same downstream region can trade off a few pixels of
        // approach cost as the bot shuffles on the source platform. Replacing the committed edge
        // every AI tick creates oscillation loops like the John 2026-05-01 down-jump trace,
        // where nav flips between a straight DROP and a nearby JUMP before either can execute.
        return current.type != AgentNavigationGraph.EdgeType.WALK
                && replacement.type != AgentNavigationGraph.EdgeType.WALK;
    }
}
