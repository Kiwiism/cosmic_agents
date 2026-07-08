package server.agents.integration;

import client.Character;

/**
 * Temporary Agent-owned bridge for ammo-sharing timing/replies while ammo
 * transfer execution still lives in the legacy bot runtime.
 */
public final class AgentBotAmmoRuntime {
    private AgentBotAmmoRuntime() {
    }

    public static void sayMapNow(Character bot, String message) {
        AgentReplyRuntime.sayMapNow(bot, message);
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentSchedulerRuntime.afterDelay(delayMs, action);
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        AgentSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
    }

    public static long randomDelayMs(int minMs, int maxMs) {
        return AgentSchedulerRuntime.randomDelayMs(minMs, maxMs);
    }
}
