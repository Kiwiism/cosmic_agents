package server.agents.integration;

/**
 * Agent-owned session scheduler adapter. Relog/logout/away session flows
 * should depend on this narrow boundary instead of the broad scheduler.
 */
public final class AgentBotSessionSchedulerRuntime {
    private AgentBotSessionSchedulerRuntime() {
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        AgentBotSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
    }
}
