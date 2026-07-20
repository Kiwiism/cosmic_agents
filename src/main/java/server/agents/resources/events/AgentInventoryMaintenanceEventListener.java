package server.agents.resources.events;

import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.mailbox.AgentMailboxOptions;

/** Converts inventory capacity facts into coalesced next-tick maintenance evaluation. */
public final class AgentInventoryMaintenanceEventListener implements AgentEventListener<AgentEvent> {
    private final AgentRuntimeEntry entry;

    public AgentInventoryMaintenanceEventListener(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        if (!(event instanceof AgentInventoryThresholdChangedEvent threshold)) {
            return;
        }
        AgentMailboxRuntime.submit(entry, runtimeEntry -> {
            runtimeEntry.capabilityStates()
                    .require(AgentInventoryMaintenanceEvaluationState.STATE_KEY)
                    .project(threshold);
            return null;
        }, AgentMailboxOptions.coalesceLatest("inventory-maintenance:" + threshold.inventoryType()));
    }
}
