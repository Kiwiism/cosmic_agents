package server.agents.integration;

/**
 * Agent-owned movement scheduler adapter. Movement/follow/greeting callbacks
 * should depend on this narrow boundary instead of the broad scheduler runtime.
 */
public final class AgentBotMovementSchedulerRuntime {
    private AgentBotMovementSchedulerRuntime() {
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        AgentBotSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
    }
}
