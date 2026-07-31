package server.agents.journey;

import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;

/** Thin session listener forwarding immutable facts to any active journey projection. */
public final class AgentJourneyEventListener implements AgentEventListener<AgentEvent> {
    @Override
    public void onAgentEvent(AgentEvent event) {
        AgentJourneyRuntime.onEvent(event);
    }
}
