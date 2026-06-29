package server.agents.integration;

import server.bots.BotEntry;

/**
 * Temporary Agent-owned bridge for party-quest dialogue while PQ automation
 * still lives in the legacy bot runtime.
 */
public final class AgentBotPqRuntime {
    private AgentBotPqRuntime() {
    }

    public static void queueSay(BotEntry entry, String message) {
        AgentBotPqReplyRuntime.queueSay(entry, message);
    }

    public static void resetKpqStage5Claimed(BotEntry entry) {
        entry.resetKpqStage5Claimed();
    }

    public static boolean kpqStage5Claimed(BotEntry entry) {
        return entry.kpq.stage5Claimed;
    }

    public static void markKpqStage5Claimed(BotEntry entry) {
        entry.kpq.stage5Claimed = true;
    }

    public static int kpqCouponTarget(BotEntry entry) {
        return entry.kpq.couponTarget;
    }

    public static int kpqStageState(BotEntry entry) {
        return entry.kpq.state;
    }

    public static void setKpqStageState(BotEntry entry, int state) {
        entry.kpq.state = state;
    }

    public static boolean kpqStageStateIs(BotEntry entry, int state) {
        return entry.kpq.state == state;
    }

    public static boolean kpqStageStateAtLeast(BotEntry entry, int state) {
        return entry.kpq.state >= state;
    }

    public static void setKpqCouponTarget(BotEntry entry, int target) {
        entry.kpq.couponTarget = target;
    }

    public static int kpqLastReportedCoupons(BotEntry entry) {
        return entry.kpq.lastReportedCoupons;
    }

    public static void setKpqLastReportedCoupons(BotEntry entry, int coupons) {
        entry.kpq.lastReportedCoupons = coupons;
    }

    public static void resetKpqStage1(BotEntry entry, int idleState) {
        entry.kpq.state = idleState;
        entry.kpq.couponTarget = -1;
        entry.kpq.waitUntilMs = 0;
        entry.kpq.lastReportedCoupons = 0;
        AgentBotScriptTaskStateRuntime.resetScript(entry, null);
    }
}
