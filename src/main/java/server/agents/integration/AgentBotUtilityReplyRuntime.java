package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned utility reply adapter. Utility chat flows should depend on this
 * narrow boundary instead of the broad reply runtime.
 */
public final class AgentBotUtilityReplyRuntime {
    private AgentBotUtilityReplyRuntime() {
    }

    public static void replyNow(BotEntry entry, String message) {
        AgentBotReplyRuntime.replyNow(entry, message);
    }
}
