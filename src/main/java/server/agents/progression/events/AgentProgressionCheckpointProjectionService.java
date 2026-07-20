package server.agents.progression.events;

import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;
import server.agents.progression.AgentCareerProgressionCheckpointRuntime;
import server.agents.progression.AgentCareerProgressionState;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.mailbox.AgentMailboxOptions;
import server.agents.monitoring.AgentEventReactionMetrics;

/** Coalesces progression changes into a next-tick durable career checkpoint. */
public final class AgentProgressionCheckpointProjectionService implements AgentEventListener<AgentEvent> {
    private static final String MAILBOX_KEY = "progression-checkpoint";
    private final AgentRuntimeEntry entry;

    public AgentProgressionCheckpointProjectionService(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        if (!(event instanceof AgentLevelChangedEvent)
                && !(event instanceof AgentJobAdvancedEvent)
                && !(event instanceof AgentQuestStateChangedEvent)) {
            return;
        }
        if (entry.capabilityStates().find(AgentCareerProgressionState.STATE_KEY).isEmpty()) {
            return;
        }
        var submission = AgentMailboxRuntime.submit(entry, runtimeEntry -> {
            AgentCareerProgressionCheckpointRuntime.persistIfDirty(
                    runtimeEntry, System.currentTimeMillis());
            return null;
        }, AgentMailboxOptions.coalesceLatest(MAILBOX_KEY));
        AgentEventReactionMetrics.record(MAILBOX_KEY, submission);
    }
}
