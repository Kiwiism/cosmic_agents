package server.agents.integration;

/**
 * Agent-owned pending-action scheduler adapter. Pending chat action flows
 * should depend on this narrow boundary instead of the broad scheduler.
 */
public final class AgentBotPendingActionSchedulerRuntime {
    private AgentBotPendingActionSchedulerRuntime() {
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        AgentBotSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
    }
}
