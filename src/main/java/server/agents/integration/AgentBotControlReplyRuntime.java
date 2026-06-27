package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned control reply adapter. Toggle, buff-query, and respec control
 * flows should depend on this narrow boundary instead of the broad reply
 * runtime.
 */
public final class AgentBotControlReplyRuntime {
    private AgentBotControlReplyRuntime() {
    }

    public static void replyNow(BotEntry entry, String message) {
        AgentBotReplyRuntime.replyNow(entry, message);
    }
}
