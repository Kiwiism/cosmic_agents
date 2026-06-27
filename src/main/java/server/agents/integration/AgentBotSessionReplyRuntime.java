package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned session reply adapter. Relog/logout/away session flows should
 * depend on this narrow boundary instead of the broad reply runtime.
 */
public final class AgentBotSessionReplyRuntime {
    private AgentBotSessionReplyRuntime() {
    }

    public static void replyNow(BotEntry entry, String message) {
        AgentBotReplyRuntime.replyNow(entry, message);
    }
}
