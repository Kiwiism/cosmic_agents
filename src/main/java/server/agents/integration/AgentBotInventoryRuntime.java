package server.agents.integration;

import server.bots.BotEntry;

/**
 * Temporary Agent-owned bridge for inventory, trade, drop, and meso reply
 * delivery while the inventory automation logic still lives in the legacy bot
 * runtime.
 */
public final class AgentBotInventoryRuntime {
    private AgentBotInventoryRuntime() {
    }

    public static void replyNow(BotEntry entry, String message) {
        AgentBotInventoryReplyRuntime.replyNow(entry, message);
    }

    public static void visibleSayNow(BotEntry entry, String message) {
        AgentBotInventoryReplyRuntime.visibleSayNow(entry, message);
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentBotInventorySchedulerRuntime.afterDelay(delayMs, action);
    }
}
