package server.agents.runtime;

import client.Character;
import server.agents.runtime.AgentFarmAnchorStateRuntime;
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;
import server.agents.integration.AgentNavigationDebugStateRuntime;
import server.agents.integration.AgentOwnerMotionStateRuntime;
import server.agents.runtime.AgentPatrolStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;

import java.awt.Point;

/**
 * Agent-owned state maintenance rules that run from the tick shell.
 */
public final class AgentTickStateMaintenanceService {
    private AgentTickStateMaintenanceService() {
    }

    public static void updateObservedLeaderMotion(AgentRuntimeEntry entry, Point leaderPosition) {
        if (entry == null || leaderPosition == null) {
            return;
        }
        AgentOwnerMotionStateRuntime.updateObservedOwnerStep(entry, leaderPosition);
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

    public static void markPreciseNavigationTargetIfNeeded(AgentRuntimeEntry entry) {
        if (AgentMoveTargetStateRuntime.isPrecise(entry)
                && !AgentNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)) {
            AgentNavigationDebugStateRuntime.setNavPreciseTarget(entry, true);
        }
    }
}
