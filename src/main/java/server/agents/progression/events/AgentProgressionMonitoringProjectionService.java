package server.agents.progression.events;

import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;
import server.agents.runtime.AgentRuntimeEntry;

/** Projects progression facts into a bounded session-local read model. */
public final class AgentProgressionMonitoringProjectionService implements AgentEventListener<AgentEvent> {
    private final AgentRuntimeEntry entry;

    public AgentProgressionMonitoringProjectionService(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        entry.capabilityStates().require(AgentProgressionEventProjectionState.STATE_KEY).record(event);
    }
}
