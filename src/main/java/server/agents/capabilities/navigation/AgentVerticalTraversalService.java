package server.agents.capabilities.navigation;

import client.Character;
import server.agents.capabilities.movement.AgentClimbStateRuntime;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Generic policy for committing a complete vertical crossing rather than unrelated rope edges.
 */
public final class AgentVerticalTraversalService {
    private AgentVerticalTraversalService() {
    }

    @FunctionalInterface
    public interface ExitEdgeFinder {
        AgentNavigationGraph.Edge findExitEdge(AgentNavigationGraph graph,
                                               Character bot,
                                               Point startPosition,
                                               int startRegionId,
                                               int targetRegionId,
                                               Point targetPosition);
    }

    public record TraversalDirective(AgentNavigationGraph.Edge edge,
                                     Point targetPosition,
                                     int targetRegionId,
                                     boolean holdGroundedExit) {
        public TraversalDirective {
            targetPosition = targetPosition == null ? null : new Point(targetPosition);
        }

        @Override
        public Point targetPosition() {
            return targetPosition == null ? null : new Point(targetPosition);
        }
    }

    /**
     * Starts a transaction only when the selected edge enters a rope and the route has a matching
     * climb exit. The one extra route lookup happens once per rope crossing, never per tick.
     */
    public static boolean beginIfRopeEntry(AgentNavigationGraph graph,
                                           AgentRuntimeEntry entry,
                                           Character bot,
                                           AgentNavigationGraph.Edge selectedEdge,
                                           int targetRegionId,
                                           Point targetPosition,
                                           ExitEdgeFinder exitEdgeFinder) {
        if (graph == null || entry == null || bot == null || selectedEdge == null
                || targetRegionId < 0
                || !AgentNavigationRopeEdgeService.isRopeEntryEdge(graph, selectedEdge)) {
            return false;
        }

        AgentVerticalTraversalState state = entry.verticalTraversalState();
        if (state.belongsTo(graph)) {
            return true;
        }
        state.clear();

        AgentNavigationGraph.Edge exitEdge = exitEdgeFinder.findExitEdge(
                graph, bot, selectedEdge.endPoint, selectedEdge.toRegionId, targetRegionId, targetPosition);
        if (!isMatchingExit(selectedEdge, exitEdge)) {
            return false;
        }

        state.begin(graph, selectedEdge, exitEdge, targetPosition, targetRegionId);
        return true;
    }

    /**
     * Returns the edge owned by the active transaction. A single grounded AI hand-off is held at
     * the destination so the same resolve call cannot immediately plan back onto the rope.
     */
    public static TraversalDirective resolve(AgentNavigationGraph graph,
                                             AgentRuntimeEntry entry,
                                             Character bot,
                                             int currentRegionId,
                                             boolean runAiTick,
                                             AgentNavigationCommittedEdgeService.EdgeUsabilityChecker usabilityChecker) {
        if (entry == null) {
            return null;
        }
        AgentVerticalTraversalState state = entry.verticalTraversalState();
        if (!state.active()) {
            return null;
        }
        if (!state.belongsTo(graph)) {
            state.clear();
            return null;
        }

        AgentNavigationGraph.Edge entryEdge = state.entryEdge();
        AgentNavigationGraph.Edge exitEdge = state.exitEdge();
        if (AgentClimbStateRuntime.climbing(entry)) {
            state.observeRopeAttachment();
        }

        AgentNavigationGraph.Edge committedEdge;
        if (AgentClimbStateRuntime.climbing(entry)) {
            committedEdge = exitEdge;
        } else if (AgentMovementStateRuntime.inAir(entry)) {
            committedEdge = state.ropeAttachmentObserved() ? exitEdge : entryEdge;
        } else if (!state.ropeAttachmentObserved()
                && (currentRegionId == entryEdge.fromRegionId || currentRegionId == entryEdge.toRegionId)) {
            committedEdge = entryEdge;
        } else if (state.ropeAttachmentObserved() && currentRegionId == exitEdge.fromRegionId) {
            committedEdge = exitEdge;
        } else if (state.ropeAttachmentObserved() && currentRegionId == exitEdge.toRegionId) {
            if (!state.groundedExitObserved()) {
                state.observeGroundedExit();
                return directive(state, null, true);
            }
            if (!runAiTick) {
                return directive(state, null, true);
            }
            state.clear();
            return null;
        } else {
            state.clear();
            return null;
        }

        if (!usabilityChecker.isUsable(graph, bot, committedEdge)) {
            state.clear();
            return null;
        }
        return directive(state, committedEdge, false);
    }

    public static int committedClimbDirection(AgentRuntimeEntry entry, int entryY) {
        if (entry == null || !entry.verticalTraversalState().active()) {
            return 0;
        }
        return Integer.compare(entry.verticalTraversalState().exitEdge().startPoint.y, entryY);
    }

    private static TraversalDirective directive(AgentVerticalTraversalState state,
                                                AgentNavigationGraph.Edge edge,
                                                boolean holdGroundedExit) {
        return new TraversalDirective(
                edge, state.targetPosition(), state.targetRegionId(), holdGroundedExit);
    }

    private static boolean isMatchingExit(AgentNavigationGraph.Edge entryEdge,
                                          AgentNavigationGraph.Edge exitEdge) {
        return exitEdge != null
                && exitEdge.type == AgentNavigationGraph.EdgeType.CLIMB
                && exitEdge.fromRegionId == entryEdge.toRegionId
                && exitEdge.toRegionId != entryEdge.fromRegionId;
    }
}
