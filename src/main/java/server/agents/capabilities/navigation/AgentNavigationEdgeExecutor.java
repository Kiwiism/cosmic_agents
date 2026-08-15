package server.agents.capabilities.navigation;

import client.Character;
import server.agents.capabilities.movement.AgentClimbStateRuntime;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Agent-owned edge execution dispatch for navigation.
 */
public final class AgentNavigationEdgeExecutor implements AgentTraversalExecutor {
    public static final AgentNavigationEdgeExecutor INSTANCE = new AgentNavigationEdgeExecutor();

    private AgentNavigationEdgeExecutor() {
    }

    @Override
    public AgentTraversalResult execute(
            AgentRuntimeEntry entry, Character agent, AgentTraversalCommand command) {
        AgentNavigationGraph graph = command.graph();
        AgentNavigationGraph.Edge edge = command.edge();
        Point agentPos = command.agentPosition();
        Point targetPosition = command.requestedTargetPosition();
        boolean executed = switch (edge.type) {
            case JUMP -> AgentNavigationJumpExecutionService.tryExecuteJump(graph, entry, agent, edge);
            case FLASH_JUMP -> AgentNavigationFlashJumpExecutionService.tryExecuteFlashJump(
                    graph, entry, agent, edge);
            case TELEPORT -> AgentNavigationTeleportExecutionService.tryExecuteTeleport(
                    entry, agent, agentPos, edge);
            case DROP -> AgentNavigationDropExecutionService.tryExecuteDrop(
                    graph, entry, agent, agentPos, edge);
            case CLIMB -> tryExecuteClimb(graph, entry, agent, agentPos, edge);
            case PORTAL -> AgentNavigationEdgeReadinessService.isReadyForEdge(agentPos, edge)
                    && AgentNavigationPortalService.tryExecutePortal(entry, agent, edge.portalId);
            default -> false;
        };
        return executed
                ? AgentTraversalResult.executed(targetPosition, true)
                : AgentTraversalResult.deferred("executor-not-ready");
    }

    private static boolean tryExecuteClimb(AgentNavigationGraph graph,
                                           AgentRuntimeEntry entry,
                                           Character agent,
                                           Point agentPos,
                                           AgentNavigationGraph.Edge edge) {
        if (AgentMovementStateRuntime.inAir(entry) || AgentMovementStateRuntime.downJumpPending(entry)) {
            return false;
        }

        return AgentClimbStateRuntime.climbing(entry)
                ? AgentNavigationClimbExitExecutionService.tryExecuteClimbExit(graph, entry, agent, agentPos, edge)
                : AgentNavigationClimbEntryExecutionService.tryExecuteClimbEntry(graph, entry, agent, agentPos, edge);
    }
}
