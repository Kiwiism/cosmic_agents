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
        return entry.shopState().visitPending();
    }

    public static boolean shopSequenceActive(BotEntry entry) {
        return entry.shopState().sequenceActive();
    }

    public static boolean hasActiveShopTransition(BotEntry entry) {
        return entry.shopState().hasActiveTransition();
    }

    public static Point shopNpcPosition(BotEntry entry) {
        return entry.shopState().npcPosition();
    }

    public static Point shopTargetPosition(BotEntry entry) {
        return entry.shopState().targetPosition();
    }

    public static Point activeShopTargetPosition(BotEntry entry) {
        return entry.shopState().activeTargetPosition();
    }

    public static int shopApproachDelayMs(BotEntry entry) {
        return entry.shopState().approachDelayMs();
    }

    public static void setShopApproachDelayMs(BotEntry entry, int delayMs) {
        entry.shopState().setApproachDelayMs(delayMs);
    }

    public static boolean shopSellTrashPending(BotEntry entry) {
        return entry.shopState().sellTrashPending();
    }

    public static void setShopSellTrashPending(BotEntry entry, boolean pending) {
        entry.shopState().setSellTrashPending(pending);
    }

    public static boolean hasShopNpcPosition(BotEntry entry) {
        return entry.shopState().hasNpcPosition();
    }

    public static boolean visitTimedOut(BotEntry entry, long nowMs, long timeoutMs) {
        return entry.shopState().visitTimedOut(nowMs, timeoutMs);
    }

    public static boolean sequenceTimedOut(BotEntry entry, long nowMs, long timeoutMs) {
        return entry.shopState().sequenceTimedOut(nowMs, timeoutMs);
    }

    public static void startShopVisit(BotEntry entry, Point npcPosition, Point targetPosition, int approachDelayMs,
                                      long startedAtMs) {
        entry.shopState().startVisit(npcPosition, targetPosition, approachDelayMs, startedAtMs);
    }

    public static Point shopTargetOrNpcPosition(BotEntry entry) {
        return entry.shopState().activeTargetPosition();
    }

    public static void markShopSequenceActive(BotEntry entry, long startedAtMs) {
        entry.shopState().markSequenceActive(startedAtMs);
    }

    public static boolean stuckNearNpc(BotEntry entry, Point botPosition, long nowMs, long fallbackMs,
                                       int moveTolerancePx, int arriveDistance) {
        return entry.shopState().stuckNearNpc(botPosition, nowMs, fallbackMs, moveTolerancePx, arriveDistance);
    }

    public static boolean sequenceValid(BotEntry entry, Point botPosition, Point npcPosition, int arriveDistance) {
        return entry.shopState().sequenceValid(botPosition, npcPosition, arriveDistance);
    }

    public static boolean shouldRunScheduledShopStep(BotEntry entry) {
        return entry.shopState().visitPending();
    }

    public static void clearShopState(BotEntry entry) {
        entry.shopState().clear();
    }
}
