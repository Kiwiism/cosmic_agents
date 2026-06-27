package server.agents.integration;

/**
 * Agent-owned equipment scheduler adapter. Equipment chat flows should depend
 * on this narrow boundary instead of the broad scheduler.
 */
public final class AgentBotEquipmentSchedulerRuntime {
    private AgentBotEquipmentSchedulerRuntime() {
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        AgentBotSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
    }
}
