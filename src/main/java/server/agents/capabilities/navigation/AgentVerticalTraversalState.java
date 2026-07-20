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

    public void clear() {
        graph = null;
        entryEdge = null;
        exitEdge = null;
        targetPosition = null;
        targetRegionId = -1;
        ropeAttachmentObserved = false;
        groundedExitObserved = false;
    }
}
