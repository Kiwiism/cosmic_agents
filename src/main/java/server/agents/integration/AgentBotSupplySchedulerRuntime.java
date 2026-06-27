package server.agents.integration;

/**
 * Agent-owned supply scheduler adapter. Supply request callbacks should depend
 * on this narrow boundary instead of the broad scheduler runtime.
 */
public final class AgentBotSupplySchedulerRuntime {
    private AgentBotSupplySchedulerRuntime() {
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        AgentBotSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
    }
}
