package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Temporary Agent-owned bridge for Maker automation replies and delayed batch
 * steps while Maker execution still lives in the legacy bot runtime.
 */
public final class AgentBotMakerRuntime {
    private AgentBotMakerRuntime() {
    }

    public static void replyNow(AgentRuntimeEntry entry, String message) {
        AgentBotReplyRuntime.replyNow(entry, message);
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentBotSchedulerRuntime.afterDelay(delayMs, action);
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        AgentBotSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
    }
}
