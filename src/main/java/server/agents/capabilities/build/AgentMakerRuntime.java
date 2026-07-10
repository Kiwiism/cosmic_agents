package server.agents.capabilities.build;

import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/** Connects Maker capability replies and delayed batch steps to shared Agent services. */
public final class AgentMakerRuntime {
    private AgentMakerRuntime() {
    }

    public static void replyNow(AgentRuntimeEntry entry, String message) {
        AgentReplyRuntime.replyNow(entry, message);
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentSchedulerRuntime.afterDelay(delayMs, action);
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        AgentSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
    }
}
