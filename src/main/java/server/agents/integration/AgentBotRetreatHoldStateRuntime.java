package server.agents.integration;

import server.bots.BotEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary BotEntry-backed ranged retreat hold state.
 */
public final class AgentBotRetreatHoldStateRuntime {
    private AgentBotRetreatHoldStateRuntime() {
    }

    public static boolean hasHold(BotEntry entry) {
        return entry.retreatHoldState().hasHold();
    }

    public static boolean hasActiveHold(BotEntry entry, long nowMs) {
        return entry.retreatHoldState().active(nowMs);
    }

    public static Point holdPosition(BotEntry entry) {
        return entry.retreatHoldState().position();
    }

    public static long holdUntilMs(BotEntry entry) {
        return entry.retreatHoldState().untilMs();
    }

    public static int distanceFromHoldX(BotEntry entry, Point botPos) {
        Point hold = holdPosition(entry);
        return Math.abs(hold.x - botPos.x);
    }

    public static void setHold(BotEntry entry, Point position, long untilMs) {
        entry.retreatHoldState().set(position, untilMs);
    }

    public static void clear(BotEntry entry) {
        entry.retreatHoldState().clear();
    }
}
