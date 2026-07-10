package server.agents.capabilities.inventory;

import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/** Connects inventory capability replies and delayed actions to shared Agent services. */
public final class AgentInventoryRuntime {
    private AgentInventoryRuntime() {
    }

    public static void replyNow(AgentRuntimeEntry entry, String message) {
        AgentReplyRuntime.replyNow(entry, message);
    }

    public static void visibleSayNow(AgentRuntimeEntry entry, String message) {
        AgentReplyRuntime.visibleSayNow(entry, message);
    }

    public static void afterDelay(AgentRuntimeEntry entry, long delayMs, Runnable action) {
        AgentSchedulerRuntime.afterDelay(entry, delayMs, action);
    }

    @Deprecated(forRemoval = true)
    public static void afterDelay(long delayMs, Runnable action) {
        AgentSchedulerRuntime.afterDelay(delayMs, action);
    }
}
