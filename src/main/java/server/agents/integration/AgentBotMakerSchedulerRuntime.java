package server.agents.integration;

/**
 * Agent-owned Maker scheduler adapter. Maker automation flows should depend
 * on this narrow boundary instead of the broad scheduler.
 */
public final class AgentBotMakerSchedulerRuntime {
    private AgentBotMakerSchedulerRuntime() {
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentBotSchedulerRuntime.afterDelay(delayMs, action);
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        AgentBotSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
    }
}
