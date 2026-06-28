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
}
