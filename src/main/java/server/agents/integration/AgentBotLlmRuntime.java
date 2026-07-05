package server.agents.integration;

import server.bots.BotEntry;

/**
 * Temporary Agent-owned bridge for LLM reply delivery while LLM orchestration
 * still lives in the legacy bot runtime.
 */
public final class AgentBotLlmRuntime {
    private AgentBotLlmRuntime() {
    }

    public static void replyNow(BotEntry entry, String message) {
        AgentBotReplyRuntime.replyNow(entry, message);
    }
}
