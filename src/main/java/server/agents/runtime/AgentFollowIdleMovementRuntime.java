package server.agents.runtime;

import client.Character;
import server.bots.BotEntry;
import server.bots.BotMovementManager;

import java.awt.Point;

/**
 * Temporary Agent-owned runtime bridge for parked-follow idle movement config.
 */
public final class AgentFollowIdleMovementRuntime {
    private AgentFollowIdleMovementRuntime() {
    }

    public static boolean tryFollowIdleMovementFastPath(BotEntry entry,
                                                        Character agent,
                                                        Point targetPosition,
                                                        long nowMs) {
        return AgentFollowIdleMovementService.tryFollowIdleMovementFastPath(
                entry,
                agent,
                targetPosition,
                nowMs,
                BotMovementManager.configuredFollowDist(),
                BotMovementManager.configuredStopDist());
    }
}
