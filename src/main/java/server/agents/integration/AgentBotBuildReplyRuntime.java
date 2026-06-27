package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned build reply adapter. AP/SP/job build flows should depend on this
 * narrow boundary instead of the broad reply runtime.
 */
public final class AgentBotBuildReplyRuntime {
    private AgentBotBuildReplyRuntime() {
    }

    public static void replyNow(BotEntry entry, String message) {
        AgentBotReplyRuntime.replyNow(entry, message);
    }

    public static void queueReply(BotEntry entry, String message) {
        AgentBotReplyRuntime.queueReply(entry, message);
    }
}
