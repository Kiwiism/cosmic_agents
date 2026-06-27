package server.agents.integration;

/**
 * Agent-owned scroll-reaction scheduler adapter. Scroll reaction timing should
 * depend on this narrow boundary instead of the broad scheduler runtime.
 */
public final class AgentBotScrollReactionSchedulerRuntime {
    private AgentBotScrollReactionSchedulerRuntime() {
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentBotSchedulerRuntime.afterDelay(delayMs, action);
    }

    public static long randomDelayMs(int minMs, int maxMs) {
        return AgentBotSchedulerRuntime.randomDelayMs(minMs, maxMs);
    }
}
