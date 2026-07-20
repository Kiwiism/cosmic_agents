package server.agents.capabilities.presentation;

import server.agents.events.AgentDomainEvent;
import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;
import server.agents.operations.events.AgentCombatTargetChangedEvent;
import server.agents.operations.events.AgentMapTransitionedEvent;
import server.agents.operations.events.AgentMobKilledEvent;
import server.agents.personality.AgentPersonalityState;
import server.agents.progression.events.AgentQuestStateChangedEvent;
import server.agents.runtime.AgentRuntimeEntry;

/** Converts meaningful Agent events into at most one pending cosmetic decision. */
public final class AgentPersonalityPresentationEventListener implements AgentEventListener<AgentEvent> {
    private final AgentRuntimeEntry entry;

    public AgentPersonalityPresentationEventListener(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        AgentPresentationTrigger trigger = trigger(event);
        if (trigger != null) {
            AgentPersonalityPresentationRuntime.schedule(entry, trigger, event.occurredAtMs());
        }
    }

    private AgentPresentationTrigger trigger(AgentEvent event) {
        if (event == null || entry == null
                || !entry.capabilityStates().require(AgentPersonalityState.STATE_KEY)
                        .presentationEnabled()) {
            return null;
        }
        if (event instanceof AgentMapTransitionedEvent) {
            return AgentPresentationTrigger.ARRIVAL;
        }
        if (event instanceof AgentQuestStateChangedEvent quest && quest.status() == 2) {
            return AgentPresentationTrigger.OBJECTIVE_COMPLETED;
        }
        if (event instanceof AgentMobKilledEvent) {
            return AgentPresentationTrigger.MOB_KILLED;
        }
        if (event instanceof AgentCombatTargetChangedEvent target) {
            return target.targetObjectId() == 0
                    ? AgentPresentationTrigger.COMBAT_IDLE
                    : AgentPresentationTrigger.COMBAT_ENGAGED;
        }
        if (event instanceof AgentDomainEvent domain
                && "objective.succeeded".equals(domain.type())) {
            return AgentPresentationTrigger.OBJECTIVE_COMPLETED;
        }
        return null;
    }
}
