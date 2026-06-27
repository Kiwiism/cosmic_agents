package server.agents.integration;

/**
 * Temporary Agent-owned bridge for combat-owned timing while combat execution
 * still lives in the legacy bot runtime.
 */
public final class AgentBotCombatRuntime {
    private AgentBotCombatRuntime() {
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentBotSchedulerRuntime.afterDelay(delayMs, action);
    }
}
