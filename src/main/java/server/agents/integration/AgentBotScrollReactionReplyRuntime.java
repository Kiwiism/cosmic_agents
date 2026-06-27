package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned scroll-reaction reply adapter. Scroll reaction dialogue should
 * depend on this narrow boundary instead of the broad reply runtime.
 */
public final class AgentBotScrollReactionReplyRuntime {
    private AgentBotScrollReactionReplyRuntime() {
    }

    public static void queueSay(BotEntry entry, String message) {
        AgentBotReplyRuntime.queueSay(entry, message);
    }
}
