package server.agents.capabilities.dialogue.llm.context;

import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;
import server.agents.runtime.AgentRuntimeEntry;

/** Projects event facts into the session-local LLM context read model. */
public final class AgentLlmContextProjectionService implements AgentEventListener<AgentEvent> {
    private final AgentRuntimeEntry entry;

    public AgentLlmContextProjectionService(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        entry.capabilityStates().require(AgentLlmContextProjectionState.STATE_KEY).record(event);
    }
}
