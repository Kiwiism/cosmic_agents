package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned movement reply adapter. Movement/follow/greeting flows should
 * depend on this narrow boundary instead of the broad reply runtime.
 */
public final class AgentBotMovementReplyRuntime {
    private AgentBotMovementReplyRuntime() {
    }

    public static void replyNow(BotEntry entry, String message) {
        AgentBotReplyRuntime.replyNow(entry, message);
    }

    public static void queueReply(BotEntry entry, String message) {
        AgentBotReplyRuntime.queueReply(entry, message);
    }
}
