package server.agents.integration;

/**
 * Temporary Agent-owned bridge for potion-sharing timing while potion transfer
 * execution still lives in the legacy bot runtime.
 */
public final class AgentBotPotionRuntime {
    private AgentBotPotionRuntime() {
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentBotSchedulerRuntime.afterDelay(delayMs, action);
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        AgentBotSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
    }

    public static long randomDelayMs(int minMs, int maxMs) {
        return AgentBotSchedulerRuntime.randomDelayMs(minMs, maxMs);
    }
}
