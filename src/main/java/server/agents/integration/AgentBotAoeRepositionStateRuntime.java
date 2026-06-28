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
        return entry.aoeRepositionAnchor();
    }

    public static boolean hasAnchor(BotEntry entry) {
        return entry.hasAoeRepositionAnchor();
    }

    public static long deadlineMs(BotEntry entry) {
        return entry.aoeRepositionDeadlineMs();
    }

    public static void setAnchor(BotEntry entry, Point anchor, long deadlineMs) {
        entry.setAoeRepositionAnchor(anchor, deadlineMs);
    }

    public static void clear(BotEntry entry) {
        entry.clearAoeRepositionAnchor();
    }

    public static boolean isExpiredOrArrived(BotEntry entry, Point botPos, long nowMs, int arrivalX) {
        Point anchor = anchor(entry);
        return anchor == null
                || nowMs > deadlineMs(entry)
                || botPos == null
                || Math.abs(anchor.x - botPos.x) <= arrivalX;
    }
}
