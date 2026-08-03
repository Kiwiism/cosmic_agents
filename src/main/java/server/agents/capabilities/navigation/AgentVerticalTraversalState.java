package server.agents.capabilities.navigation;

import java.awt.Point;

/**
 * Per-Agent state for one committed ground-to-rope-to-ground traversal.
 *
 * <p>The ordinary active-edge state represents only the edge currently being executed. A rope
 * crossing needs slightly wider continuity because its ground entry edge is replaced by a rope
 * exit edge after attachment. Keeping that pair together prevents a moving target from reversing
 * the route in the short hand-off between those edges.</p>
 */
public final class AgentVerticalTraversalState {
    private AgentNavigationGraph graph;
    private AgentNavigationGraph.Edge entryEdge;
    private AgentNavigationGraph.Edge exitEdge;
    private Point targetPosition;
    private int targetRegionId = -1;
    private boolean ropeAttachmentObserved;
    private boolean groundedExitObserved;
    private AgentNavigationGraph recentExitGraph;
    private int recentExitRopeRegionId = -1;
    private int recentExitGroundRegionId = -1;
    private Point recentExitNudgeTarget;
    private long recentExitGuardUntilMs;

    void begin(AgentNavigationGraph graph,
               AgentNavigationGraph.Edge entryEdge,
               AgentNavigationGraph.Edge exitEdge,
               Point targetPosition,
               int targetRegionId) {
        this.graph = graph;
        this.entryEdge = entryEdge;
        this.exitEdge = exitEdge;
        this.targetPosition = targetPosition == null ? null : new Point(targetPosition);
        this.targetRegionId = targetRegionId;
        ropeAttachmentObserved = false;
        groundedExitObserved = false;
    }

    public boolean active() {
        return graph != null && entryEdge != null && exitEdge != null;
    }

    boolean belongsTo(AgentNavigationGraph graph) {
        return active() && this.graph == graph;
    }

    AgentNavigationGraph.Edge entryEdge() {
        return entryEdge;
    }

    AgentNavigationGraph.Edge exitEdge() {
        return exitEdge;
    }

    Point targetPosition() {
        return targetPosition == null ? null : new Point(targetPosition);
    }

    int targetRegionId() {
        return targetRegionId;
    }

    boolean ropeAttachmentObserved() {
        return ropeAttachmentObserved;
    }

    void observeRopeAttachment() {
        ropeAttachmentObserved = true;
    }

    boolean groundedExitObserved() {
        return groundedExitObserved;
    }

    void observeGroundedExit() {
        groundedExitObserved = true;
    }

    void complete(long nowMs, long reentryBlockMs, int nudgePx) {
        recentExitGraph = graph;
        recentExitRopeRegionId = exitEdge.fromRegionId;
        recentExitGroundRegionId = exitEdge.toRegionId;
        recentExitNudgeTarget = selectExitNudgeTarget(graph, exitEdge, nudgePx);
        recentExitGuardUntilMs = nowMs + Math.max(1L, reentryBlockMs);
        clearActive();
    }

    boolean blocksRecentInverseEntry(AgentNavigationGraph graph,
                                     AgentNavigationGraph.Edge edge,
                                     long nowMs) {
        expireRecentExitGuard(nowMs);
        return recentExitGraph == graph
                && edge != null
                && edge.type == AgentNavigationGraph.EdgeType.CLIMB
                && edge.fromRegionId == recentExitGroundRegionId
                && edge.toRegionId == recentExitRopeRegionId;
    }

    boolean hasRecentExitGuard(AgentNavigationGraph graph, int currentRegionId, long nowMs) {
        expireRecentExitGuard(nowMs);
        return recentExitGraph == graph && currentRegionId == recentExitGroundRegionId;
    }

    Point recentExitNudgeTarget() {
        return recentExitNudgeTarget == null ? null : new Point(recentExitNudgeTarget);
    }

    public void clear() {
        clearActive();
        recentExitGraph = null;
        recentExitRopeRegionId = -1;
        recentExitGroundRegionId = -1;
        recentExitNudgeTarget = null;
        recentExitGuardUntilMs = 0L;
    }

    private void clearActive() {
        graph = null;
        entryEdge = null;
        exitEdge = null;
        targetPosition = null;
        targetRegionId = -1;
        ropeAttachmentObserved = false;
        groundedExitObserved = false;
    }

    private void expireRecentExitGuard(long nowMs) {
        if (recentExitGraph != null && nowMs >= recentExitGuardUntilMs) {
            recentExitGraph = null;
            recentExitRopeRegionId = -1;
            recentExitGroundRegionId = -1;
            recentExitNudgeTarget = null;
            recentExitGuardUntilMs = 0L;
        }
    }

    private static Point selectExitNudgeTarget(AgentNavigationGraph graph,
                                               AgentNavigationGraph.Edge exitEdge,
                                               int nudgePx) {
        AgentNavigationGraph.Region ground = graph.getRegion(exitEdge.toRegionId);
        if (ground == null || ground.isRopeRegion) {
            return new Point(exitEdge.endPoint);
        }

        int ropeX = exitEdge.ropeX;
        int direction = Integer.compare(exitEdge.endPoint.x, ropeX);
        if (direction == 0) {
            direction = ropeX - ground.minX <= ground.maxX - ropeX ? 1 : -1;
        }
        int targetX = Math.clamp(ropeX + direction * Math.max(1, nudgePx), ground.minX, ground.maxX);
        return ground.pointAt(targetX);
    }
}
