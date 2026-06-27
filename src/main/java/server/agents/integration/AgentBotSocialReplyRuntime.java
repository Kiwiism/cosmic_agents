package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned social reply adapter. Fame/social dialogue should depend on this
 * narrow boundary instead of the broad reply runtime.
 */
public final class AgentBotSocialReplyRuntime {
    private AgentBotSocialReplyRuntime() {
    }

    public static void replyNow(BotEntry entry, String message) {
        AgentBotReplyRuntime.replyNow(entry, message);
    }
}
