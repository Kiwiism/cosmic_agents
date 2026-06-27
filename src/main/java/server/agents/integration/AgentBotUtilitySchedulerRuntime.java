package server.agents.integration;

/**
 * Agent-owned utility scheduler adapter. Utility chat callbacks should depend
 * on this narrow boundary instead of the broad scheduler runtime.
 */
public final class AgentBotUtilitySchedulerRuntime {
    private AgentBotUtilitySchedulerRuntime() {
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        AgentBotSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
    }
}
