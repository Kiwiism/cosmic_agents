package server.agents.integration;

/**
 * Agent-owned status scheduler adapter. AFK and welcome-back status flows
 * should depend on this narrow boundary instead of the broad scheduler.
 */
public final class AgentBotStatusSchedulerRuntime {
    private AgentBotStatusSchedulerRuntime() {
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        AgentBotSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
    }
}
