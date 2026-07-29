package server.agents.capabilities.navigation;

import client.Character;
import server.agents.capabilities.movement.AgentClimbStateRuntime;
import server.agents.capabilities.movement.AgentJumpActionService;
import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.capabilities.movement.AgentMovementSkillState;
import server.agents.capabilities.movement.AgentMovementSkillStateRuntime;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/** Starts the normal jump phase of one validated Flash Jump graph edge. */
public final class AgentNavigationFlashJumpExecutionService {
    private AgentNavigationFlashJumpExecutionService() {
    }

    public static boolean tryExecuteFlashJump(AgentNavigationGraph graph,
                                              AgentRuntimeEntry entry,
                                              Character agent,
                                              AgentNavigationGraph.Edge edge) {
        if (AgentMovementStateRuntime.inAir(entry) || AgentClimbStateRuntime.climbing(entry)) {
            return false;
        }
        Point position = agent.getPosition();
        int launchX = AgentNavigationWaypointService.selectJumpLaunchX(entry, graph, edge);
        int tolerance = Math.max(1, AgentMovementKinematicsService.walkStep(
                agent.getMap(), AgentMovementStateRuntime.movementProfile(entry)));
        if (!AgentNavigationEdgeReadinessService.canExecuteSelectedJumpFromCurrentPosition(
                graph, position, edge, launchX, tolerance)) {
            AgentNavigationDebugStateRuntime.setLastEdgeBlockReason(entry, "flash-jump-pos");
            return false;
        }

        long nowMs = System.currentTimeMillis();
        if (!AgentMovementSkillPolicy.canExecute(entry, agent, edge, nowMs)) {
            AgentNavigationDebugStateRuntime.setLastEdgeBlockReason(entry, "flash-jump-policy");
            return false;
        }
        int mpCost = AgentMovementSkillPolicy.mpCost(agent, edge.type);
        if (!agent.applyHpMpChange(0, 0, -mpCost)) {
            return false;
        }

        AgentNavigationDebugStateRuntime.clearLastEdgeBlockReason(entry);
        AgentNavigationEdgeExecutionStateService.setEdgeExecutionTarget(entry, edge);
        AgentJumpActionService.initiateJump(entry, agent, edge.launchStepX);
        AgentMovementSkillState state = AgentMovementSkillStateRuntime.state(entry);
        state.setFlashJumpPending(true);
        state.setFlashJumpFired(false);
        AgentMovementSkillStateRuntime.recordCast(entry, nowMs);
        return true;
    }
}
