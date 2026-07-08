package server.agents.integration;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Temporary Agent-owned bridge for shop automation replies and delayed shop
 * steps while shop execution still lives in the legacy bot runtime.
 */
public final class AgentBotShopRuntime {
    private AgentBotShopRuntime() {
    }

    public static void replyNow(AgentRuntimeEntry entry, String message) {
        AgentReplyRuntime.replyNow(entry, message);
    }

    public static void sayMapNow(Character bot, String message) {
        AgentReplyRuntime.sayMapNow(bot, message);
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentSchedulerRuntime.afterDelay(delayMs, action);
    }

    public static long randomDelayMs(int minMs, int maxMs) {
        return AgentSchedulerRuntime.randomDelayMs(minMs, maxMs);
    }
}
