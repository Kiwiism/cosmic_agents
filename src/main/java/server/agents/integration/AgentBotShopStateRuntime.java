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

    public static void setShopApproachDelayMs(BotEntry entry, int delayMs) {
        entry.setShopApproachDelayMs(delayMs);
    }

    public static boolean shopSellTrashPending(BotEntry entry) {
        return entry.shopSellTrashPending();
    }

    public static void setShopSellTrashPending(BotEntry entry, boolean pending) {
        entry.setShopSellTrashPending(pending);
    }

    public static boolean hasShopNpcPosition(BotEntry entry) {
        return entry.shopNpcPos() != null;
    }

    public static boolean visitTimedOut(BotEntry entry, long nowMs, long timeoutMs) {
        return entry.shopVisitStartedAtMs() > 0
                && !entry.shopSequenceActive()
                && nowMs - entry.shopVisitStartedAtMs() > timeoutMs;
    }

    public static boolean sequenceTimedOut(BotEntry entry, long nowMs, long timeoutMs) {
        return entry.shopSequenceActive()
                && entry.shopSequenceStartedAtMs() > 0
                && nowMs - entry.shopSequenceStartedAtMs() > timeoutMs;
    }

    public static void startShopVisit(BotEntry entry, Point npcPosition, Point targetPosition, int approachDelayMs,
                                      long startedAtMs) {
        entry.startShopVisit(npcPosition, targetPosition, approachDelayMs, startedAtMs);
    }

    public static Point shopTargetOrNpcPosition(BotEntry entry) {
        Point target = entry.shopTargetPos();
        return target != null ? target : entry.shopNpcPos();
    }

    public static void markShopSequenceActive(BotEntry entry, long startedAtMs) {
        entry.markShopSequenceActive(startedAtMs);
    }

    public static boolean stuckNearNpc(BotEntry entry, Point botPosition, long nowMs, long fallbackMs,
                                       int moveTolerancePx, int arriveDistance) {
        return entry.stuckNearShopNpc(botPosition, nowMs, fallbackMs, moveTolerancePx, arriveDistance);
    }

    public static boolean sequenceValid(BotEntry entry, Point botPosition, Point npcPosition, int arriveDistance) {
        if (!entry.shopVisitPending() || !entry.shopSequenceActive() || npcPosition == null || botPosition == null) {
            return false;
        }
        Point approach = entry.shopTargetPos() != null ? entry.shopTargetPos() : npcPosition;
        return manhattan(botPosition, approach) <= arriveDistance
                || manhattan(botPosition, npcPosition) <= arriveDistance;
    }

    public static boolean shouldRunScheduledShopStep(BotEntry entry) {
        return entry.shopVisitPending();
    }

    public static void clearShopState(BotEntry entry) {
        entry.clearShopState();
    }

    private static int manhattan(Point a, Point b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }
}
