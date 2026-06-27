package server.agents.integration;

import client.Character;

/**
 * Temporary Agent-owned bridge for potion-sharing timing/replies while potion
 * transfer execution still lives in the legacy bot runtime.
 */
public final class AgentBotPotionRuntime {
    private AgentBotPotionRuntime() {
    }

    public static void sayMapNow(Character bot, String message) {
        AgentBotPotionReplyRuntime.sayMapNow(bot, message);
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentBotPotionSchedulerRuntime.afterDelay(delayMs, action);
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        AgentBotPotionSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
    }

    public static long randomDelayMs(int minMs, int maxMs) {
        return AgentBotPotionSchedulerRuntime.randomDelayMs(minMs, maxMs);
    }
}
