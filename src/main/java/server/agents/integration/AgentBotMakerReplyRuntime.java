package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned Maker reply adapter. Maker automation flows should depend on
 * this narrow boundary instead of the broad reply runtime.
 */
public final class AgentBotMakerReplyRuntime {
    private AgentBotMakerReplyRuntime() {
    }

    public static void replyNow(BotEntry entry, String message) {
        AgentBotReplyRuntime.replyNow(entry, message);
    }
}
