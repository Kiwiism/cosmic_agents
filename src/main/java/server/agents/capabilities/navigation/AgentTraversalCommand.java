package server.agents.capabilities.navigation;

import java.awt.Point;

/** One fully selected and validated navigation edge presented for execution. */
public record AgentTraversalCommand(
        AgentNavigationGraph graph,
        AgentNavigationGraph.Edge edge,
        Point agentPosition,
        Point requestedTargetPosition) {

    public AgentTraversalCommand {
        if (graph == null || edge == null || agentPosition == null
                || requestedTargetPosition == null) {
            throw new IllegalArgumentException("a traversal command requires graph, edge, and positions");
        }
        agentPosition = new Point(agentPosition);
        requestedTargetPosition = new Point(requestedTargetPosition);
    }

    @Override
    public Point agentPosition() {
        return new Point(agentPosition);
    }

    @Override
    public Point requestedTargetPosition() {
        return new Point(requestedTargetPosition);
    }
}
