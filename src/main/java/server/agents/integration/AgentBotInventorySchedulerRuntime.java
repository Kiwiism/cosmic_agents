package server.agents.integration;

/**
 * Agent-owned inventory scheduler adapter. Inventory, trade, drop, and meso
 * flows should depend on this narrow boundary instead of the broad scheduler.
 */
public final class AgentBotInventorySchedulerRuntime {
    private AgentBotInventorySchedulerRuntime() {
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentBotSchedulerRuntime.afterDelay(delayMs, action);
    }
}
