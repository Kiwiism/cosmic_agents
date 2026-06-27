package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned inventory reply adapter. Inventory, trade, drop, and meso flows
 * should depend on this narrow boundary instead of the broad reply runtime.
 */
public final class AgentBotInventoryReplyRuntime {
    private AgentBotInventoryReplyRuntime() {
    }

    public static void replyNow(BotEntry entry, String message) {
        AgentBotReplyRuntime.replyNow(entry, message);
    }

    public static void visibleSayNow(BotEntry entry, String message) {
        AgentBotReplyRuntime.visibleSayNow(entry, message);
    }
}
