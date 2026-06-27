package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned LLM reply adapter. LLM dialogue flows should depend on this
 * narrow boundary instead of the broad reply runtime.
 */
public final class AgentBotLlmReplyRuntime {
    private AgentBotLlmReplyRuntime() {
    }

    public static void replyNow(BotEntry entry, String message) {
        AgentBotReplyRuntime.replyNow(entry, message);
    }
}
