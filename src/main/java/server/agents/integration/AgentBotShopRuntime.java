package server.agents.integration;

import client.Character;
import server.bots.BotEntry;

/**
 * Temporary Agent-owned bridge for shop automation replies and delayed shop
 * steps while shop execution still lives in the legacy bot runtime.
 */
public final class AgentBotShopRuntime {
    private AgentBotShopRuntime() {
    }

    public static void replyNow(BotEntry entry, String message) {
        AgentBotShopReplyRuntime.replyNow(entry, message);
    }

    public static void sayMapNow(Character bot, String message) {
        AgentBotShopReplyRuntime.sayMapNow(bot, message);
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentBotShopSchedulerRuntime.afterDelay(delayMs, action);
    }

    public static long randomDelayMs(int minMs, int maxMs) {
        return AgentBotShopSchedulerRuntime.randomDelayMs(minMs, maxMs);
    }
}
