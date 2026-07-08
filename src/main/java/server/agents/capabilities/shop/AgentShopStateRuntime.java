package server.agents.capabilities.shop;

import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed shop transition state.
 */
public final class AgentShopStateRuntime {
    private AgentShopStateRuntime() {
    }

    public static boolean shopVisitPending(AgentRuntimeEntry entry) {
        return entry.shopState().visitPending();
    }

    public static boolean shopSequenceActive(AgentRuntimeEntry entry) {
        return entry.shopState().sequenceActive();
    }

    public static boolean hasActiveShopTransition(AgentRuntimeEntry entry) {
        return entry.shopState().hasActiveTransition();
    }

    public static Point shopNpcPosition(AgentRuntimeEntry entry) {
        return entry.shopState().npcPosition();
    }

    public static Point shopTargetPosition(AgentRuntimeEntry entry) {
        return entry.shopState().targetPosition();
    }

    public static Point activeShopTargetPosition(AgentRuntimeEntry entry) {
        return entry.shopState().activeTargetPosition();
    }

    public static int shopApproachDelayMs(AgentRuntimeEntry entry) {
        return entry.shopState().approachDelayMs();
    }

    public static void setShopApproachDelayMs(AgentRuntimeEntry entry, int delayMs) {
        entry.shopState().setApproachDelayMs(delayMs);
    }

    public static boolean shopSellTrashPending(AgentRuntimeEntry entry) {
        return entry.shopState().sellTrashPending();
    }

    public static void setShopSellTrashPending(AgentRuntimeEntry entry, boolean pending) {
        entry.shopState().setSellTrashPending(pending);
    }

    public static boolean hasShopNpcPosition(AgentRuntimeEntry entry) {
        return entry.shopState().hasNpcPosition();
    }

    public static boolean visitTimedOut(AgentRuntimeEntry entry, long nowMs, long timeoutMs) {
        return entry.shopState().visitTimedOut(nowMs, timeoutMs);
    }

    public static boolean sequenceTimedOut(AgentRuntimeEntry entry, long nowMs, long timeoutMs) {
        return entry.shopState().sequenceTimedOut(nowMs, timeoutMs);
    }

    public static void startShopVisit(AgentRuntimeEntry entry, Point npcPosition, Point targetPosition, int approachDelayMs,
                                      long startedAtMs) {
        entry.shopState().startVisit(npcPosition, targetPosition, approachDelayMs, startedAtMs);
    }

    public static Point shopTargetOrNpcPosition(AgentRuntimeEntry entry) {
        return entry.shopState().activeTargetPosition();
    }

    public static void markShopSequenceActive(AgentRuntimeEntry entry, long startedAtMs) {
        entry.shopState().markSequenceActive(startedAtMs);
    }

    public static boolean stuckNearNpc(AgentRuntimeEntry entry, Point botPosition, long nowMs, long fallbackMs,
                                       int moveTolerancePx, int arriveDistance) {
        return entry.shopState().stuckNearNpc(botPosition, nowMs, fallbackMs, moveTolerancePx, arriveDistance);
    }

    public static boolean sequenceValid(AgentRuntimeEntry entry, Point botPosition, Point npcPosition, int arriveDistance) {
        return entry.shopState().sequenceValid(botPosition, npcPosition, arriveDistance);
    }

    public static boolean shouldRunScheduledShopStep(AgentRuntimeEntry entry) {
        return entry.shopState().visitPending();
    }

    public static void clearShopState(AgentRuntimeEntry entry) {
        entry.shopState().clear();
    }
}
