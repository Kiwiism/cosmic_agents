package server.agents.runtime;

import server.bots.BotEntry;
import server.bots.BotMovementManager;

import java.awt.Point;

/**
 * Temporary Agent-owned runtime bridge for local attack movement-window config.
 */
public final class AgentLocalAttackMoveWindowRuntime {
    private AgentLocalAttackMoveWindowRuntime() {
    }

    public static void setLocalAttackMoveWindow(BotEntry entry, Point agentPosition, Point referencePosition) {
        AgentLocalAttackMoveWindowService.setLocalAttackMoveWindow(
                entry,
                agentPosition,
                referencePosition,
                BotMovementManager.configuredFollowDist(),
                BotMovementManager.configuredStopDist(),
                BotMovementManager.configuredFollowYCap());
    }

    public static void clearFollowActionMoveWindowIfSettled(BotEntry entry,
                                                            Point agentPosition,
                                                            AgentTargetSnapshot targetSnapshot) {
        AgentLocalAttackMoveWindowService.clearFollowActionMoveWindowIfSettled(
                entry,
                agentPosition,
                targetSnapshot,
                BotMovementManager.configuredFollowDist(),
                BotMovementManager.configuredStopDist(),
                BotMovementManager.configuredFollowYCap());
    }

    public static void clearActionMoveWindowIfSettled(BotEntry entry,
                                                      Point agentPosition,
                                                      Point targetPosition) {
        AgentLocalAttackMoveWindowService.clearActionMoveWindowIfSettled(
                entry,
                agentPosition,
                targetPosition,
                BotMovementManager.configuredFollowDist(),
                BotMovementManager.configuredStopDist(),
                BotMovementManager.configuredFollowYCap());
    }
}
