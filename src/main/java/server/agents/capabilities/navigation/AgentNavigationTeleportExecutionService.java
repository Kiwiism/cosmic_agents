package server.agents.capabilities.navigation;

import client.Character;
import server.agents.capabilities.movement.AgentClimbStateRuntime;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.capabilities.movement.AgentMovementSkillStateRuntime;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/** Executes one validated Teleport graph edge. */
public final class AgentNavigationTeleportExecutionService {
    private AgentNavigationTeleportExecutionService() {
    }

    public static boolean tryExecuteTeleport(AgentRuntimeEntry entry,
                                             Character agent,
                                             Point agentPosition,
                                             AgentNavigationGraph.Edge edge) {
        if (AgentMovementStateRuntime.inAir(entry)
                || AgentClimbStateRuntime.climbing(entry)
                || !AgentNavigationEdgeReadinessService.isReadyForEdge(agentPosition, edge)) {
            return false;
        }
        long nowMs = System.currentTimeMillis();
        if (!AgentMovementSkillPolicy.canExecute(entry, agent, edge, nowMs)) {
            return false;
        }
        int mpCost = AgentMovementSkillPolicy.mpCost(agent, edge.type);
        if (!agent.applyHpMpChange(0, 0, -mpCost)) {
            return false;
        }

        Point origin = new Point(agentPosition);
        Point destination = new Point(edge.endPoint);
        AgentMovementPoseService.teleportTo(entry, agent, destination);
        AgentMovementSkillStateRuntime.recordCast(entry, nowMs);
        AgentMovementBroadcastService.broadcastTeleport(entry, origin, destination);
        AgentMovementStateResetService.clearNavigationState(entry);
        return true;
    }
}
