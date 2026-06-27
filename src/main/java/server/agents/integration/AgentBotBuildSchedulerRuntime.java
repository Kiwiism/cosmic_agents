package server.agents.integration;

/**
 * Agent-owned build scheduler adapter. AP/SP/job build callbacks should depend
 * on this narrow boundary instead of the broad scheduler runtime.
 */
public final class AgentBotBuildSchedulerRuntime {
    private AgentBotBuildSchedulerRuntime() {
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        AgentBotSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
    }
}
