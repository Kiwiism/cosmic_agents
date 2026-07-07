package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;

import client.Character;

import java.awt.Point;

/**
 * Temporary Agent-owned runtime bridge for parked-follow idle movement config.
 */
public final class AgentFollowIdleMovementRuntime {
    private AgentFollowIdleMovementRuntime() {
    }

    public static boolean tryFollowIdleMovementFastPath(AgentRuntimeEntry entry,
                                                        Character agent,
                                                        Point targetPosition,
                                                        long nowMs) {
        return AgentFollowIdleMovementService.tryFollowIdleMovementFastPath(
                entry,
                agent,
                targetPosition,
                nowMs,
                AgentMovementPhysicsConfig.configuredFollowDist(),
                AgentMovementPhysicsConfig.configuredStopDist());
    }
}
