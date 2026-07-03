package server.agents.capabilities.navigation;

import client.Character;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
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

    @FunctionalInterface
    public interface EdgeUsabilityChecker {
        boolean isUsable(AgentNavigationGraph graph, Character bot, AgentNavigationGraph.Edge edge);
    }

    @FunctionalInterface
    public interface RopeEntryChecker {
        boolean isRopeEntry(AgentNavigationGraph graph, AgentNavigationGraph.Edge edge);
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

    public static AgentNavigationGraph.Edge reuseCommittedEdge(AgentNavigationGraph graph,
                                                               BotEntry entry,
                                                               int startRegionId,
                                                               int targetRegionId,
                                                               EdgeUsabilityChecker edgeUsabilityChecker,
                                                               RopeEntryChecker ropeEntryChecker) {
        AgentNavigationGraph.Edge edge = (AgentNavigationGraph.Edge) AgentBotNavigationDebugStateRuntime.activeNavigationEdge(entry);
        if (edge == null) {
            return null;
        }
        if (targetRegionId < 0) {
            return null;
        }
        int previousTargetRegionId = AgentBotNavigationDebugStateRuntime.navTargetRegionId(entry);
        // Update stored target in-place rather than discarding. The Y-snap offset causes
        // followBase.x to differ between AI and non-AI ticks, making targetRegionId fluctuate
        // even when the owner hasn't meaningfully moved. Relying on structural checks below
        // (start-region match, usability, arrival) is sufficient to detect actual invalidity.
        AgentBotNavigationDebugStateRuntime.setNavTargetRegionId(entry, targetRegionId);
        if (!edgeUsabilityChecker.isUsable(graph, AgentBotRuntimeIdentityRuntime.bot(entry), edge)) {
            return null;
        }
        if (AgentBotClimbStateRuntime.climbing(entry) && ropeEntryChecker.isRopeEntry(graph, edge)) {
            return null;
        }
        if (startRegionId == edge.toRegionId && !AgentBotMovementStateRuntime.inAir(entry) && !AgentBotClimbStateRuntime.climbing(entry)
                && edge.fromRegionId != edge.toRegionId) {
            // Self-loop edges (intra-region portals) inherently start and end in the same
            // region. Completion is signalled by execution, not by a region change.
            return null;
        }
        // Once the resolved target is back in the bot's current region, any committed edge that
        // would leave that region is stale. Keeping it causes follow/formation loops where the
        // bot repeatedly runs toward an old jump/drop/portal after the live follow target has
        // snapped back onto the current platform.
        if (!AgentBotMovementStateRuntime.inAir(entry) && !AgentBotClimbStateRuntime.climbing(entry)
                && startRegionId >= 0 && startRegionId == targetRegionId
                && edge.toRegionId != startRegionId) {
            return null;
        }
        if (startRegionId == edge.fromRegionId) {
            if (!AgentBotMovementStateRuntime.inAir(entry) && !AgentBotClimbStateRuntime.climbing(entry)
                    && previousTargetRegionId >= 0
                    && previousTargetRegionId != targetRegionId
                    && edge.toRegionId != targetRegionId) {
                return null;
            }
            return edge;
        }
        // While climbing, always keep the edge. Ground foothold lookup can report the platform
        // below/behind the rope as current region and otherwise drop the exit edge too early.
        if (AgentBotClimbStateRuntime.climbing(entry) && (startRegionId < 0 || startRegionId != edge.toRegionId)) {
            return edge;
        }
        // DROP/JUMP arcs may enter the destination region before the bot touches down. Keep the
        // edge until landing when the bot is in a region consistent with this arc.
        if (AgentBotMovementStateRuntime.inAir(entry) && (startRegionId < 0 || startRegionId == edge.toRegionId)
                && (edge.type == AgentNavigationGraph.EdgeType.DROP
                    || edge.type == AgentNavigationGraph.EdgeType.JUMP)) {
            return edge;
        }
        if (AgentBotMovementStateRuntime.inAir(entry) && edge.type == AgentNavigationGraph.EdgeType.CLIMB && edge.launchStepX != 0) {
            // Rope-exit jump arcs use the same sampled ballistic model as JUMP/DROP edges.
            return edge;
        }
        return null;
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
