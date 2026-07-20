package server.agents.resources.events;

import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;
import server.agents.runtime.AgentRuntimeEntry;

/** Projects resource facts into a bounded session-local read model. */
public final class AgentResourceMonitoringProjectionService implements AgentEventListener<AgentEvent> {
    private final AgentRuntimeEntry entry;

    public AgentResourceMonitoringProjectionService(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        entry.capabilityStates().require(AgentResourceEventProjectionState.STATE_KEY).record(event);
    }
}
