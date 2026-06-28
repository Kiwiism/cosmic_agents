package server.agents.integration;

import server.bots.BotEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary BotEntry-backed shop transition state.
 */
public final class AgentBotShopStateRuntime {
    private AgentBotShopStateRuntime() {
    }

    public static boolean shopVisitPending(BotEntry entry) {
        return entry.shopVisitPending();
    }

    public static boolean shopSequenceActive(BotEntry entry) {
        return entry.shopSequenceActive();
    }

    public static boolean hasActiveShopTransition(BotEntry entry) {
        return shopVisitPending(entry) || shopSequenceActive(entry);
    }

    public static Point shopNpcPosition(BotEntry entry) {
        return entry.shopNpcPos();
    }

    public static Point shopTargetPosition(BotEntry entry) {
        return entry.shopTargetPos();
    }

    public static Point activeShopTargetPosition(BotEntry entry) {
        return entry.activeShopTargetPos();
    }

    public static int shopApproachDelayMs(BotEntry entry) {
        return entry.shopApproachDelayMs();
    }
}
