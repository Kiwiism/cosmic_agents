package server.agents.operations.events;

import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.mailbox.AgentMailboxOptions;

/** Defers operational blocker projection to the next Agent tick. */
public final class AgentOperationalEvaluationListener implements AgentEventListener<AgentEvent> {
    private static final String MAILBOX_KEY = "operational-evaluation";
    private final AgentRuntimeEntry entry;

    public AgentOperationalEvaluationListener(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        if (!(event instanceof AgentNavigationRouteFailedEvent)
                && !(event instanceof AgentStuckDetectedEvent)
                && !(event instanceof AgentRecoveryPerformedEvent)
                && !(event instanceof AgentMapTransitionedEvent)
                && !(event instanceof AgentLifeStateChangedEvent)) {
            return;
        }
        AgentMailboxRuntime.submit(entry, runtimeEntry -> {
            runtimeEntry.capabilityStates().require(AgentOperationalEvaluationState.STATE_KEY)
                    .project(event);
            return null;
        }, AgentMailboxOptions.coalesceLatest(MAILBOX_KEY));
    }
}
