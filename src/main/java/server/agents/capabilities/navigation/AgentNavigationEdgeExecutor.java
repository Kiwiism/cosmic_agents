package server.agents.capabilities.navigation;

import client.Character;
import server.agents.integration.AgentClimbStateRuntime;
import server.agents.integration.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Agent-owned edge execution dispatch for navigation.
 */
public final class AgentNavigationEdgeExecutor {
    private AgentNavigationEdgeExecutor() {
    }

    public record NavigationDirective(Point targetPos, boolean consumedTick) {
    }

    public static NavigationDirective tryExecuteEdge(AgentNavigationGraph graph,
                                                     AgentRuntimeEntry entry,
                                                     Character agent,
                                                     Point agentPos,
                                                     Point rawTargetPos,
                                                     AgentNavigationGraph.Edge edge,
                                                     boolean runAiTick) {
        if (!runAiTick) {
            return null;
        }

        return switch (edge.type) {
            case JUMP -> AgentNavigationJumpExecutionService.tryExecuteJump(graph, entry, agent, edge)
                    ? new NavigationDirective(rawTargetPos, true) : null;
            case DROP -> AgentNavigationDropExecutionService.tryExecuteDrop(graph, entry, agent, agentPos, edge)
                    ? new NavigationDirective(rawTargetPos, true) : null;
            case CLIMB -> tryExecuteClimb(graph, entry, agent, agentPos, rawTargetPos, edge);
            case PORTAL -> AgentNavigationEdgeReadinessService.isReadyForEdge(agentPos, edge)
                    && AgentNavigationPortalService.tryExecutePortal(entry, agent, edge.portalId)
                    ? new NavigationDirective(rawTargetPos, true) : null;
            default -> null;
        };
    }

    private static NavigationDirective tryExecuteClimb(AgentNavigationGraph graph,
                                                       AgentRuntimeEntry entry,
                                                       Character agent,
                                                       Point agentPos,
                                                       Point rawTargetPos,
                                                       AgentNavigationGraph.Edge edge) {
        if (AgentMovementStateRuntime.inAir(entry) || AgentMovementStateRuntime.downJumpPending(entry)) {
            return null;
        }

        boolean executed = AgentClimbStateRuntime.climbing(entry)
                ? AgentNavigationClimbExitExecutionService.tryExecuteClimbExit(graph, entry, agent, agentPos, edge)
                : AgentNavigationClimbEntryExecutionService.tryExecuteClimbEntry(graph, entry, agent, agentPos, edge);
        return executed ? new NavigationDirective(rawTargetPos, true) : null;
    }
}
