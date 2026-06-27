package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned pending-action reply adapter. Pending chat action flows should
 * depend on this narrow boundary instead of the broad reply runtime.
 */
public final class AgentBotPendingActionReplyRuntime {
    private AgentBotPendingActionReplyRuntime() {
    }

    public static void replyNow(BotEntry entry, String message) {
        AgentBotReplyRuntime.replyNow(entry, message);
    }

    public static void queueReply(BotEntry entry, String message) {
        AgentBotReplyRuntime.queueReply(entry, message);
    }
}
