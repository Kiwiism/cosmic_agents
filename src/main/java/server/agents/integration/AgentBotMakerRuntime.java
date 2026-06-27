package server.agents.integration;

import server.bots.BotEntry;

/**
 * Temporary Agent-owned bridge for Maker automation replies and delayed batch
 * steps while Maker execution still lives in the legacy bot runtime.
 */
public final class AgentBotMakerRuntime {
    private AgentBotMakerRuntime() {
    }

    public static void replyNow(BotEntry entry, String message) {
        AgentBotMakerReplyRuntime.replyNow(entry, message);
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentBotMakerSchedulerRuntime.afterDelay(delayMs, action);
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        AgentBotMakerSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
    }
}
