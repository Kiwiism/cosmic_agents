package server.agents.capabilities.llm;

import server.agents.integration.AgentDialogueTransportRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/** Connects LLM capability output to the shared Agent reply service. */
public final class AgentLlmRuntime {
    private AgentLlmRuntime() {
    }

    public static void replyNow(AgentRuntimeEntry entry, String message) {
        AgentDialogueTransportRuntime.replyNow(entry, message);
    }
}
