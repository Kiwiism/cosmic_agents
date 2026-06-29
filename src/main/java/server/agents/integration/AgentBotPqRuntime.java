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
}
