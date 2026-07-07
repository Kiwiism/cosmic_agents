package server.agents.capabilities.navigation;

import client.Character;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentQueuedMovementActionService;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Agent-owned explicit drop edge execution for navigation.
 */
public final class AgentNavigationDropExecutionService {
    private AgentNavigationDropExecutionService() {
    }

    public static boolean tryExecuteDrop(AgentNavigationGraph graph,
                                         AgentRuntimeEntry entry,
                                         Character agent,
                                         Point agentPos,
                                         AgentNavigationGraph.Edge edge) {
        if (AgentBotMovementStateRuntime.inAir(entry)
                || AgentBotClimbStateRuntime.climbing(entry)
                || AgentBotMovementStateRuntime.downJumpPending(entry)) {
            return false;
        }

        if (edge.launchStepX != 0) {
            return false;
        }

        if (!AgentNavigationEdgeReadinessService.canExecuteDropFromCurrentPosition(graph, agentPos, edge)) {
            return false;
        }

        AgentNavigationEdgeExecutionStateService.setEdgeExecutionTarget(entry, edge);
        AgentQueuedMovementActionService.queueDownJump(entry, agent);
        AgentMovementBroadcastService.broadcastMovement(entry);
        return true;
    }
}
