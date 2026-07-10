package server.agents.capabilities.movement;

import client.Character;
import server.agents.runtime.AgentFarmAnchorStateRuntime;
import server.agents.runtime.AgentPatrolStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Agent-owned state maintenance rules that run from the tick shell.
 */
public final class AgentMovementTargetMaintenanceService {
    private AgentMovementTargetMaintenanceService() {
    }

    public static void clearFarmAnchorOnMapChange(AgentRuntimeEntry entry, Character agent) {
        if (entry == null || agent == null || !AgentFarmAnchorStateRuntime.hasFarmAnchor(entry)) {
            return;
        }
        if (AgentFarmAnchorStateRuntime.clearFarmAnchorIfMapChanged(entry, agent.getMapId())) {
            if (AgentMoveTargetStateRuntime.isPrecise(entry)) {
                AgentMoveTargetStateRuntime.clearMoveTarget(entry);
            }
        }
    }

    public static void clearReachedMoveTarget(AgentRuntimeEntry entry, int normalArrivalDistance) {
        if (!AgentMoveTargetStateRuntime.hasMoveTarget(entry)) {
            return;
        }
        Point agentPosition = AgentRuntimeIdentityRuntime.botPosition(entry);
        if (AgentMoveTargetStateRuntime.hasReachedMoveTarget(entry, agentPosition, normalArrivalDistance)) {
            AgentMoveTargetStateRuntime.clearMoveTarget(entry);
        }
    }

    public static void clearPatrolOnMapChange(AgentRuntimeEntry entry, Character agent) {
        if (entry == null || agent == null || !AgentPatrolStateRuntime.hasPatrolRegion(entry)) {
            return;
        }
        AgentPatrolStateRuntime.clearPatrolIfMapChanged(entry, agent.getMapId());
    }

}
