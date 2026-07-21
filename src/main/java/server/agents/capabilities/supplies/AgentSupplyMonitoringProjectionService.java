package server.agents.capabilities.supplies;

import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;
import server.agents.runtime.AgentRuntimeEntry;

/** Maintains a bounded session-local diagnostic projection from supply events. */
public final class AgentSupplyMonitoringProjectionService implements AgentEventListener<AgentEvent> {
    private final AgentRuntimeEntry entry;

    public AgentSupplyMonitoringProjectionService(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        if (event instanceof AgentSupplyThresholdChangedEvent threshold) {
            entry.capabilityStates().require(AgentSupplyEventMetricsState.STATE_KEY).record(threshold);
        }
    }
}
