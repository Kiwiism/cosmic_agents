package server.agents.capabilities.supplies;

import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.mailbox.AgentMailboxOptions;

/** Converts a supply fact into a coalesced next-tick maintenance evaluation. */
public final class AgentSupplyMaintenanceEventListener implements AgentEventListener<AgentEvent> {
    private final AgentRuntimeEntry entry;

    public AgentSupplyMaintenanceEventListener(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        if (!(event instanceof AgentSupplyThresholdChangedEvent threshold)) {
            return;
        }
        AgentMailboxRuntime.submit(entry, runtimeEntry -> {
            runtimeEntry.capabilityStates()
                    .require(AgentSupplyMaintenanceEvaluationState.STATE_KEY)
                    .project(threshold);
            return null;
        }, AgentMailboxOptions.coalesceLatest("supply-maintenance:" + threshold.category()));
    }
}
