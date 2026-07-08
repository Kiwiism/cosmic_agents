package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Temporary Agent-owned bridge for LLM reply delivery while LLM orchestration
 * still lives in the legacy bot runtime.
 */
public final class AgentLlmRuntime {
    private AgentLlmRuntime() {
    }

    public static void replyNow(AgentRuntimeEntry entry, String message) {
        AgentReplyRuntime.replyNow(entry, message);
    }
}
