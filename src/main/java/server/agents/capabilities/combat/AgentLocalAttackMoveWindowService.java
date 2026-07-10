package server.agents.capabilities.combat;

import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentTargetSnapshot;

import java.awt.Point;

/**
 * Agent-owned local attack move-window timing and settle rules.
 */
public final class AgentLocalAttackMoveWindowService {
    private AgentLocalAttackMoveWindowService() {
    }

    public static void setLocalAttackMoveWindow(AgentRuntimeEntry entry,
                                                Point agentPosition,
                                                Point referencePosition) {
        setLocalAttackMoveWindow(
                entry,
                agentPosition,
                referencePosition,
                AgentMovementPhysicsConfig.configuredFollowDist(),
                AgentMovementPhysicsConfig.configuredStopDist(),
                AgentMovementPhysicsConfig.configuredFollowYCap());
    }

    public static void clearFollowActionMoveWindowIfSettled(AgentRuntimeEntry entry,
                                                            Point agentPosition,
                                                            AgentTargetSnapshot targetSnapshot) {
        clearFollowActionMoveWindowIfSettled(
                entry,
                agentPosition,
                targetSnapshot,
                AgentMovementPhysicsConfig.configuredFollowDist(),
                AgentMovementPhysicsConfig.configuredStopDist(),
                AgentMovementPhysicsConfig.configuredFollowYCap());
    }

    public static void clearActionMoveWindowIfSettled(AgentRuntimeEntry entry,
                                                      Point agentPosition,
                                                      Point targetPosition) {
        clearActionMoveWindowIfSettled(
                entry,
                agentPosition,
                targetPosition,
                AgentMovementPhysicsConfig.configuredFollowDist(),
                AgentMovementPhysicsConfig.configuredStopDist(),
                AgentMovementPhysicsConfig.configuredFollowYCap());
    }

    public static void setLocalAttackMoveWindow(AgentRuntimeEntry entry,
                                                Point agentPosition,
                                                Point referencePosition,
                                                int followDistance,
                                                int stopDistance,
                                                int followYCap) {
        if (agentPosition == null || referencePosition == null) {
            AgentCombatCooldownStateRuntime.clearMoveWindow(entry);
            return;
        }

        int dx = Math.abs(agentPosition.x - referencePosition.x);
        AgentCombatCooldownStateRuntime.setMoveWindowMs(entry,
                dx > followDistance * 3 ? 1000
                        : dx > followDistance ? 200
                        : 0);
        clearActionMoveWindowIfSettled(entry, agentPosition, referencePosition, followDistance, stopDistance, followYCap);
    }

    public static void clearFollowActionMoveWindowIfSettled(AgentRuntimeEntry entry,
                                                            Point agentPosition,
                                                            AgentTargetSnapshot targetSnapshot,
                                                            int followDistance,
                                                            int stopDistance,
                                                            int followYCap) {
        if (entry == null || !AgentModeStateRuntime.following(entry) || targetSnapshot == null) {
            return;
        }
        clearActionMoveWindowIfSettled(
                entry,
                agentPosition,
                targetSnapshot.followTargetPos(),
                followDistance,
                stopDistance,
                followYCap);
    }

    public static void clearActionMoveWindowIfSettled(AgentRuntimeEntry entry,
                                                      Point agentPosition,
                                                      Point targetPosition,
                                                      int followDistance,
                                                      int stopDistance,
                                                      int followYCap) {
        if (entry == null || !AgentCombatCooldownStateRuntime.hasMoveWindow(entry)
                || agentPosition == null || targetPosition == null) {
            return;
        }

        int followStopBand = Math.max(stopDistance, followDistance);
        if (Math.abs(agentPosition.x - targetPosition.x) <= followStopBand
                && Math.abs(agentPosition.y - targetPosition.y) <= followYCap) {
            AgentCombatCooldownStateRuntime.clearMoveWindow(entry);
        }
    }
}
