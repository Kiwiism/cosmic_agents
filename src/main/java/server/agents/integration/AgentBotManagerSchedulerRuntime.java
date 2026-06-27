package server.agents.integration;

/**
 * Temporary Agent-owned bridge for delayed callbacks still triggered by the
 * legacy BotManager shell.
 */
public final class AgentBotManagerSchedulerRuntime {
    private AgentBotManagerSchedulerRuntime() {
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentBotSchedulerRuntime.afterDelay(delayMs, action);
    }
}
