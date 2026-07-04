package server.agents.integration;

import server.agents.capabilities.partyquest.kpq.AgentKpqState;
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
        state(entry).clearStage5Claimed();
    }

    public static boolean kpqStage5Claimed(BotEntry entry) {
        return state(entry).stage5Claimed();
    }

    public static void markKpqStage5Claimed(BotEntry entry) {
        state(entry).markStage5Claimed();
    }

    public static int kpqCouponTarget(BotEntry entry) {
        return state(entry).couponTarget();
    }

    public static int kpqStageState(BotEntry entry) {
        return state(entry).state();
    }

    public static void setKpqStageState(BotEntry entry, int state) {
        state(entry).setState(state);
    }

    public static boolean kpqStageStateIs(BotEntry entry, int state) {
        return state(entry).stateIs(state);
    }

    public static boolean kpqStageStateAtLeast(BotEntry entry, int state) {
        return state(entry).stateAtLeast(state);
    }

    public static void setKpqCouponTarget(BotEntry entry, int target) {
        state(entry).setCouponTarget(target);
    }

    public static int kpqLastReportedCoupons(BotEntry entry) {
        return state(entry).lastReportedCoupons();
    }

    public static void setKpqLastReportedCoupons(BotEntry entry, int coupons) {
        state(entry).setLastReportedCoupons(coupons);
    }

    public static void resetKpqStage1(BotEntry entry, int idleState) {
        state(entry).resetStage1(idleState);
        AgentBotScriptTaskStateRuntime.resetScript(entry, null);
    }

    private static AgentKpqState state(BotEntry entry) {
        return entry.kpqState();
    }
}
