package server.agents.integration;

/**
 * Agent-owned potion scheduler adapter. Potion-sharing flows should depend on
 * this narrow boundary instead of the broad scheduler.
 */
public final class AgentBotPotionSchedulerRuntime {
    private AgentBotPotionSchedulerRuntime() {
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
