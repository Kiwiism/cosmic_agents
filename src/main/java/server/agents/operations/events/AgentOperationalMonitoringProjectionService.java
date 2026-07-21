package server.agents.operations.events;

import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;
import server.agents.runtime.AgentRuntimeEntry;

/** Projects combat, navigation, and recovery facts into a bounded read model. */
public final class AgentOperationalMonitoringProjectionService implements AgentEventListener<AgentEvent> {
    private final AgentRuntimeEntry entry;

    public AgentOperationalMonitoringProjectionService(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        entry.capabilityStates().require(AgentOperationalEventProjectionState.STATE_KEY).record(event);
    }
}
