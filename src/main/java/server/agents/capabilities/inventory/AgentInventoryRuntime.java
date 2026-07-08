package server.agents.capabilities.inventory;

import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentSchedulerRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Temporary Agent-owned bridge for inventory, trade, drop, and meso reply
 * delivery while the inventory automation logic still lives in the legacy bot
 * runtime.
 */
public final class AgentInventoryRuntime {
    private AgentInventoryRuntime() {
    }

    public static void replyNow(AgentRuntimeEntry entry, String message) {
        AgentReplyRuntime.replyNow(entry, message);
    }

    public static void visibleSayNow(AgentRuntimeEntry entry, String message) {
        AgentReplyRuntime.visibleSayNow(entry, message);
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentSchedulerRuntime.afterDelay(delayMs, action);
    }
}
