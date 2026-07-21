package server.agents.events.journal;

import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;

/** Session listener that hands selected facts to the process-wide bounded writer. */
public final class AgentDurableEventJournalListener implements AgentEventListener<AgentEvent> {
    @Override
    public void onAgentEvent(AgentEvent event) {
        AgentEventJournalRuntime.offer(event);
    }
}
