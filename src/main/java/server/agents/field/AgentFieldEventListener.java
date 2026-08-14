package server.agents.field;

import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;
import server.agents.operations.events.AgentMobKilledEvent;
import server.agents.runtime.AgentRuntimeEntry;

/** Projects authoritative Agent kills into shared field-exercise progress. */
public final class AgentFieldEventListener implements AgentEventListener<AgentEvent> {
    private final AgentRuntimeEntry entry;

    public AgentFieldEventListener(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        if (event instanceof AgentMobKilledEvent killed) {
            AgentFieldRuntime.recordKill(entry, killed);
        }
    }
}
