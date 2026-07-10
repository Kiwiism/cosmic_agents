package server.agents.capabilities.social;

import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/** Connects scroll-reaction dialogue and timing to shared Agent services. */
public final class AgentScrollReactionRuntime {
    private AgentScrollReactionRuntime() {
    }

    public static void queueSay(AgentRuntimeEntry entry, String message) {
        AgentReplyRuntime.queueSay(entry, message);
    }

    public static void afterDelay(AgentRuntimeEntry entry, long delayMs, Runnable action) {
        AgentSchedulerRuntime.afterDelay(entry, delayMs, action);
    }

    @Deprecated(forRemoval = true)
    public static void afterDelay(long delayMs, Runnable action) {
        AgentSchedulerRuntime.afterDelay(delayMs, action);
    }

    public static long randomDelayMs(int minMs, int maxMs) {
        return AgentSchedulerRuntime.randomDelayMs(minMs, maxMs);
    }
}
