package server.agents.capabilities.navigation;

import client.Character;
import server.agents.capabilities.movement.AgentJumpActionService;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentQueuedMovementActionService;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Rope;

import java.awt.Point;

/**
 * Agent-owned climb-entry edge execution for navigation.
 */
public final class AgentNavigationClimbEntryExecutionService {
    private AgentNavigationClimbEntryExecutionService() {
    }

    public static boolean tryExecuteClimbEntry(AgentNavigationGraph graph,
                                               AgentRuntimeEntry entry,
                                               Character agent,
                                               Point agentPos,
                                               AgentNavigationGraph.Edge edge) {
        AgentNavigationGraph.Region toRegion = graph.getRegion(edge.toRegionId);
        Rope rope = AgentNavigationGraphService.findRopeFromRegion(agent.getMap(), toRegion);
        if (rope == null) {
            return false;
        }
        if (!AgentNavigationRopeEdgeService.canExecuteClimbEntryFromCurrentPosition(agentPos, edge, rope)) {
            AgentNavigationDebugStateRuntime.setLastEdgeBlockReason(entry, "climb-pos");
            return false;
        }

        if (AgentNavigationRopeEdgeService.canGrabRopeAtCurrentPosition(agentPos, rope)) {
            AgentNavigationDebugStateRuntime.clearLastEdgeBlockReason(entry);
            AgentNavigationClimbExecutionService.startClimbing(entry, agent, rope, agentPos.y);
            return true;
        }
        if (AgentNavigationRopeEdgeService.canAttachToRopeFromTopPlatform(edge, agentPos, rope)) {
            AgentNavigationDebugStateRuntime.clearLastEdgeBlockReason(entry);
            AgentNavigationClimbExecutionService.startClimbing(entry, agent, rope, edge.endPoint.y);
            return true;
        }
        if (AgentNavigationRopeEdgeService.canGrabRopeFromTopPlatform(edge, agentPos, rope)) {
            AgentNavigationDebugStateRuntime.clearLastEdgeBlockReason(entry);
            AgentQueuedMovementActionService.queueTopRopeEntry(entry, agent, rope, edge.endPoint.y);
            AgentMovementBroadcastService.broadcastMovement(entry);
            return true;
        }

        if (AgentNavigationRopeEdgeService.canExecuteGroundRopeJumpEntryFromCurrentPosition(agentPos, edge)) {
            AgentNavigationDebugStateRuntime.clearLastEdgeBlockReason(entry);
            AgentJumpActionService.initiateRopeJump(entry, agent, edge.launchStepX);
            return true;
        }

        AgentNavigationDebugStateRuntime.setLastEdgeBlockReason(entry, "climb-reach");
        return false;
    }
}
