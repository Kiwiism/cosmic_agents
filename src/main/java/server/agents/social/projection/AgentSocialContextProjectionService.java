package server.agents.social.projection;

import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;
import server.agents.runtime.AgentRuntimeEntry;

/** Projects event facts into the session-local LLM context read model. */
public final class AgentSocialContextProjectionService implements AgentEventListener<AgentEvent> {
    private final AgentRuntimeEntry entry;

    public AgentSocialContextProjectionService(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        entry.capabilityStates().require(AgentSocialContextProjectionState.STATE_KEY).record(event);
    }
}
