package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned transfer reply adapter. Trade/drop/item query flows should depend
 * on this narrow boundary instead of the broad reply runtime.
 */
public final class AgentBotTransferReplyRuntime {
    private AgentBotTransferReplyRuntime() {
    }

    public static void replyNow(BotEntry entry, String message) {
        AgentBotReplyRuntime.replyNow(entry, message);
    }
}
