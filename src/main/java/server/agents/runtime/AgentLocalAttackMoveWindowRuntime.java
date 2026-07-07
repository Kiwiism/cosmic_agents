package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;

import java.awt.Point;

/**
 * Temporary Agent-owned runtime bridge for local attack movement-window config.
 */
public final class AgentLocalAttackMoveWindowRuntime {
    private AgentLocalAttackMoveWindowRuntime() {
    }

    public static void setLocalAttackMoveWindow(AgentRuntimeEntry entry, Point agentPosition, Point referencePosition) {
        AgentLocalAttackMoveWindowService.setLocalAttackMoveWindow(
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
        AgentLocalAttackMoveWindowService.clearFollowActionMoveWindowIfSettled(
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
        AgentLocalAttackMoveWindowService.clearActionMoveWindowIfSettled(
                entry,
                agentPosition,
                targetPosition,
                AgentMovementPhysicsConfig.configuredFollowDist(),
                AgentMovementPhysicsConfig.configuredStopDist(),
                AgentMovementPhysicsConfig.configuredFollowYCap());
    }
}
