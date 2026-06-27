package server.agents.integration;

/**
 * Agent-owned control scheduler adapter. Toggle, buff-query, and respec
 * control flows should depend on this narrow boundary instead of the broad
 * scheduler.
 */
public final class AgentBotControlSchedulerRuntime {
    private AgentBotControlSchedulerRuntime() {
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        AgentBotSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
    }
}
