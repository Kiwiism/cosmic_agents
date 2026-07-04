package server.agents.integration;

import server.bots.BotEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary BotEntry-backed AoE reposition commitment state.
 */
public final class AgentBotAoeRepositionStateRuntime {
    private AgentBotAoeRepositionStateRuntime() {
    }

    public static Point anchor(BotEntry entry) {
        return entry.aoeRepositionState().anchor();
    }

    public static boolean hasAnchor(BotEntry entry) {
        return entry.aoeRepositionState().hasAnchor();
    }

    public static long deadlineMs(BotEntry entry) {
        return entry.aoeRepositionState().deadlineMs();
    }

    public static void setAnchor(BotEntry entry, Point anchor, long deadlineMs) {
        entry.aoeRepositionState().setAnchor(anchor, deadlineMs);
    }

    public static void clear(BotEntry entry) {
        entry.aoeRepositionState().clear();
    }

    public static boolean isExpiredOrArrived(BotEntry entry, Point botPos, long nowMs, int arrivalX) {
        return entry.aoeRepositionState().expiredOrArrived(botPos, nowMs, arrivalX);
    }
}
