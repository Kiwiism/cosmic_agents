package server.agents.integration;

import server.agents.capabilities.partyquest.kpq.AgentKpqState;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Temporary Agent-owned bridge for party-quest dialogue while PQ automation
 * still lives in the legacy bot runtime.
 */
public final class AgentBotPqRuntime {
    private AgentBotPqRuntime() {
    }

    public static void queueSay(AgentRuntimeEntry entry, String message) {
        AgentReplyRuntime.queueSay(entry, message);
    }

    public static void resetKpqStage5Claimed(AgentRuntimeEntry entry) {
        state(entry).clearStage5Claimed();
    }

    public static boolean kpqStage5Claimed(AgentRuntimeEntry entry) {
        return state(entry).stage5Claimed();
    }

    public static void markKpqStage5Claimed(AgentRuntimeEntry entry) {
        state(entry).markStage5Claimed();
    }

    public static int kpqCouponTarget(AgentRuntimeEntry entry) {
        return state(entry).couponTarget();
    }

    public static int kpqStageState(AgentRuntimeEntry entry) {
        return state(entry).state();
    }

    public static void setKpqStageState(AgentRuntimeEntry entry, int state) {
        state(entry).setState(state);
    }

    public static boolean kpqStageStateIs(AgentRuntimeEntry entry, int state) {
        return state(entry).stateIs(state);
    }

    public static boolean kpqStageStateAtLeast(AgentRuntimeEntry entry, int state) {
        return state(entry).stateAtLeast(state);
    }

    public static void setKpqCouponTarget(AgentRuntimeEntry entry, int target) {
        state(entry).setCouponTarget(target);
    }

    public static int kpqLastReportedCoupons(AgentRuntimeEntry entry) {
        return state(entry).lastReportedCoupons();
    }

    public static void setKpqLastReportedCoupons(AgentRuntimeEntry entry, int coupons) {
        state(entry).setLastReportedCoupons(coupons);
    }

    public static void resetKpqStage1(AgentRuntimeEntry entry, int idleState) {
        state(entry).resetStage1(idleState);
        AgentScriptTaskStateRuntime.resetScript(entry, null);
    }

    private static AgentKpqState state(AgentRuntimeEntry entry) {
        return entry.kpqState();
    }
}
