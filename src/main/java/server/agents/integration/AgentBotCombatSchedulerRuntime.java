package server.agents.integration;

/**
 * Agent-owned combat scheduler adapter. Combat warning/status flows should
 * depend on this narrow boundary instead of the broad scheduler.
 */
public final class AgentBotCombatSchedulerRuntime {
    private AgentBotCombatSchedulerRuntime() {
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentBotSchedulerRuntime.afterDelay(delayMs, action);
    }
}
