package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Temporary Agent-owned bridge for scroll reaction chat and timing while scroll
 * reaction decisions still live in the legacy bot runtime.
 */
public final class AgentBotScrollReactionRuntime {
    private AgentBotScrollReactionRuntime() {
    }

    public static void queueSay(AgentRuntimeEntry entry, String message) {
        AgentBotReplyRuntime.queueSay(entry, message);
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentBotSchedulerRuntime.afterDelay(delayMs, action);
    }

    public static long randomDelayMs(int minMs, int maxMs) {
        return AgentBotSchedulerRuntime.randomDelayMs(minMs, maxMs);
    }
}
