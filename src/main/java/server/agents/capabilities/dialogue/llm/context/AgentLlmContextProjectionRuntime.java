package server.agents.capabilities.dialogue.llm.context;

import server.agents.runtime.AgentRuntimeEntry;

/** Read boundary for future LLM prompt construction and planner adapters. */
public final class AgentLlmContextProjectionRuntime {
    private AgentLlmContextProjectionRuntime() {
    }

    public static AgentLlmContextProjectionState.Snapshot snapshot(AgentRuntimeEntry entry) {
        if (entry == null) {
            return new AgentLlmContextProjectionState().snapshot();
        }
        return entry.capabilityStates().require(AgentLlmContextProjectionState.STATE_KEY).snapshot();
    }
}
