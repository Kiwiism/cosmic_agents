package server.agents.capabilities.navigation;

import client.Character;
import server.agents.capabilities.movement.AgentJumpActionService;
import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.integration.AgentClimbStateRuntime;
import server.agents.integration.AgentMovementStateRuntime;
import server.agents.integration.AgentNavigationDebugStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Rope;

import java.awt.Point;

/**
 * Agent-owned jump edge execution for navigation.
 */
public final class AgentNavigationJumpExecutionService {
    private AgentNavigationJumpExecutionService() {
    }

    public static boolean tryExecuteJump(AgentNavigationGraph graph,
                                         AgentRuntimeEntry entry,
                                         Character agent,
                                         AgentNavigationGraph.Edge edge) {
        if (AgentMovementStateRuntime.inAir(entry) || AgentClimbStateRuntime.climbing(entry)) {
            return false;
        }
        Point agentPos = agent.getPosition();
        if (!canExecuteSelectedJumpFromCurrentPosition(graph, entry, agent, agentPos, edge)) {
            if (edge.startPoint.y > agentPos.y) {
                AgentNavigationGraph.Region fromRegion = graph.getRegion(edge.fromRegionId);
                if (fromRegion != null && fromRegion.isRopeRegion) {
                    Rope rope = AgentNavigationGraphService.findRopeFromRegion(agent.getMap(), fromRegion);
                    if (rope != null && AgentNavigationRopeEdgeService.canGrabRopeAtCurrentPosition(agentPos, rope)) {
                        AgentNavigationClimbExecutionService.startClimbing(entry, agent, rope, agentPos.y);
                        return true;
                    }
                }
            }
            AgentNavigationDebugStateRuntime.setLastEdgeBlockReason(entry, "jump-pos");
            return false;
        }

        AgentNavigationDebugStateRuntime.clearLastEdgeBlockReason(entry);
        AgentNavigationEdgeExecutionStateService.setEdgeExecutionTarget(entry, edge);
        AgentJumpActionService.initiateJump(entry, agent, edge.launchStepX);
        return true;
    }

    private static boolean canExecuteSelectedJumpFromCurrentPosition(AgentNavigationGraph graph,
                                                                     AgentRuntimeEntry entry,
                                                                     Character agent,
                                                                     Point agentPos,
                                                                     AgentNavigationGraph.Edge edge) {
        if (!AgentNavigationEdgeReadinessService.canExecuteJumpFromCurrentPosition(graph, agentPos, edge)) {
            return false;
        }
        int launchX = AgentNavigationWaypointService.selectJumpLaunchX(entry, graph, edge);
        int tolerance = Math.max(1, AgentMovementKinematicsService.walkStep(agent.getMap(),
                entry != null ? AgentMovementStateRuntime.movementProfile(entry) : null));
        return AgentNavigationEdgeReadinessService.canExecuteSelectedJumpFromCurrentPosition(
                graph, agentPos, edge, launchX, tolerance);
    }
}
