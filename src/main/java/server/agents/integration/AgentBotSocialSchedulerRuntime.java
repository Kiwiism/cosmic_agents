package server.agents.integration;

/**
 * Agent-owned social scheduler adapter. Fame/social callbacks should depend on
 * this narrow boundary instead of the broad scheduler runtime.
 */
public final class AgentBotSocialSchedulerRuntime {
    private AgentBotSocialSchedulerRuntime() {
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        AgentBotSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
    }
}
