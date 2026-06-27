package server.agents.integration;

/**
 * Agent-owned shop scheduler adapter. Shop automation flows should depend on
 * this narrow boundary instead of the broad scheduler.
 */
public final class AgentBotShopSchedulerRuntime {
    private AgentBotShopSchedulerRuntime() {
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentBotSchedulerRuntime.afterDelay(delayMs, action);
    }

    public static long randomDelayMs(int minMs, int maxMs) {
        return AgentBotSchedulerRuntime.randomDelayMs(minMs, maxMs);
    }
}
