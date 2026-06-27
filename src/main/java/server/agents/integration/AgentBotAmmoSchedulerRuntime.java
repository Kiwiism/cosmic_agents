package server.agents.integration;

/**
 * Agent-owned ammo scheduler adapter. Ammo-sharing flows should depend on
 * this narrow boundary instead of the broad scheduler.
 */
public final class AgentBotAmmoSchedulerRuntime {
    private AgentBotAmmoSchedulerRuntime() {
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
