package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Temporary Agent-owned bridge for LLM reply delivery while LLM orchestration
 * still lives in the legacy bot runtime.
 */
public final class AgentBotLlmRuntime {
    private AgentBotLlmRuntime() {
    }

    public static void replyNow(AgentRuntimeEntry entry, String message) {
        AgentReplyRuntime.replyNow(entry, message);
    }
}
