package server.agents.capabilities.navigation;

import server.agents.capabilities.movement.AgentClimbStateRuntime;
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Agent-owned policy for deciding whether an active navigation edge should use
 * a precise intermediate waypoint instead of loose movement steering.
 */
public final class AgentNavigationPreciseTargetService {
    private AgentNavigationPreciseTargetService() {
    }

    public static void markPreciseNavigationTargetIfNeeded(AgentRuntimeEntry entry) {
        if (AgentMoveTargetStateRuntime.isPrecise(entry)
                && !AgentNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)) {
            AgentNavigationDebugStateRuntime.setNavPreciseTarget(entry, true);
        }
    }

    public static boolean shouldUsePreciseTarget(AgentNavigationGraph graph,
                                                 AgentRuntimeEntry entry,
                                                 Point botPos,
                                                 AgentNavigationGraph.Edge edge,
                                                 EdgeReadiness readiness) {
        if (AgentMovementStateRuntime.inAir(entry)) {
            return false;
        }
        return switch (edge.type) {
            case WALK -> AgentNavigationPathService.shouldUsePreciseWalkTarget(edge);
            case JUMP -> !readiness.canExecuteSelectedJump(graph, entry, botPos, edge);
            case DROP -> edge.launchStepX == 0 && !readiness.canExecuteDrop(graph, entry, botPos, edge);
            case CLIMB -> AgentClimbStateRuntime.climbing(entry)
                    ? edge.launchStepX != 0 && !readiness.canExecuteClimbExit(graph, entry, botPos, edge)
                    : !readiness.canExecuteClimbEntry(graph, entry, botPos, edge);
            case PORTAL -> !AgentNavigationEdgeReadinessService.isReadyForEdge(botPos, edge);
        };
    }

    public interface EdgeReadiness {
        boolean canExecuteSelectedJump(AgentNavigationGraph graph, AgentRuntimeEntry entry, Point botPos, AgentNavigationGraph.Edge edge);

        boolean canExecuteDrop(AgentNavigationGraph graph, AgentRuntimeEntry entry, Point botPos, AgentNavigationGraph.Edge edge);

        boolean canExecuteClimbExit(AgentNavigationGraph graph, AgentRuntimeEntry entry, Point botPos, AgentNavigationGraph.Edge edge);

        boolean canExecuteClimbEntry(AgentNavigationGraph graph, AgentRuntimeEntry entry, Point botPos, AgentNavigationGraph.Edge edge);
    }
}
