package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotFarmAnchorStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotOwnerMotionStateRuntime;
import server.agents.integration.AgentBotPatrolStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;

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
        AgentBotOwnerMotionStateRuntime.updateObservedOwnerStep(entry, leaderPosition);
    }

    public static void clearFarmAnchorOnMapChange(BotEntry entry, Character agent) {
        if (entry == null || agent == null || !AgentBotFarmAnchorStateRuntime.hasFarmAnchor(entry)) {
            return;
        }
        if (AgentBotFarmAnchorStateRuntime.clearFarmAnchorIfMapChanged(entry, agent.getMapId())) {
            if (AgentBotMoveTargetStateRuntime.isPrecise(entry)) {
                AgentBotMoveTargetStateRuntime.clearMoveTarget(entry);
            }
        }
    }

    public static void clearReachedMoveTarget(BotEntry entry, int normalArrivalDistance) {
        if (!AgentBotMoveTargetStateRuntime.hasMoveTarget(entry)) {
            return;
        }
        Point agentPosition = AgentBotRuntimeIdentityRuntime.botPosition(entry);
        if (AgentBotMoveTargetStateRuntime.hasReachedMoveTarget(entry, agentPosition, normalArrivalDistance)) {
            AgentBotMoveTargetStateRuntime.clearMoveTarget(entry);
        }
    }

    public static void clearPatrolOnMapChange(BotEntry entry, Character agent) {
        if (entry == null || agent == null || !AgentBotPatrolStateRuntime.hasPatrolRegion(entry)) {
            return;
        }
        AgentBotPatrolStateRuntime.clearPatrolIfMapChanged(entry, agent.getMapId());
    }

    public static void markPreciseNavigationTargetIfNeeded(AgentRuntimeEntry entry) {
        if (AgentBotMoveTargetStateRuntime.isPrecise(entry)
                && !AgentBotNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)) {
            AgentBotNavigationDebugStateRuntime.setNavPreciseTarget(entry, true);
        }
    }
}
