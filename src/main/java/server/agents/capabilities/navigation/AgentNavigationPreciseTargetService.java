package server.agents.capabilities.navigation;

import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;

/**
 * Agent-owned policy for deciding whether an active navigation edge should use
 * a precise intermediate waypoint instead of loose movement steering.
 */
public final class AgentNavigationPreciseTargetService {
    private AgentNavigationPreciseTargetService() {
    }

    public static boolean shouldUsePreciseTarget(AgentNavigationGraph graph,
                                                 BotEntry entry,
                                                 Point botPos,
                                                 AgentNavigationGraph.Edge edge,
                                                 EdgeReadiness readiness) {
        if (AgentBotMovementStateRuntime.inAir(entry)) {
            return false;
        }
        return switch (edge.type) {
            case WALK -> AgentNavigationPathService.shouldUsePreciseWalkTarget(edge);
            case JUMP -> !readiness.canExecuteSelectedJump(graph, entry, botPos, edge);
            case DROP -> edge.launchStepX == 0 && !readiness.canExecuteDrop(graph, entry, botPos, edge);
            case CLIMB -> AgentBotClimbStateRuntime.climbing(entry)
                    ? edge.launchStepX != 0 && !readiness.canExecuteClimbExit(graph, entry, botPos, edge)
                    : !readiness.canExecuteClimbEntry(graph, entry, botPos, edge);
            case PORTAL -> !AgentNavigationEdgeReadinessService.isReadyForEdge(botPos, edge);
        };
    }

    public interface EdgeReadiness {
        boolean canExecuteSelectedJump(AgentNavigationGraph graph, BotEntry entry, Point botPos, AgentNavigationGraph.Edge edge);

        boolean canExecuteDrop(AgentNavigationGraph graph, BotEntry entry, Point botPos, AgentNavigationGraph.Edge edge);

        boolean canExecuteClimbExit(AgentNavigationGraph graph, BotEntry entry, Point botPos, AgentNavigationGraph.Edge edge);

        boolean canExecuteClimbEntry(AgentNavigationGraph graph, BotEntry entry, Point botPos, AgentNavigationGraph.Edge edge);
    }
}
