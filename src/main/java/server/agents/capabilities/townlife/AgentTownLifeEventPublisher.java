package server.agents.capabilities.townlife;

import client.Character;
import server.agents.events.AgentEventPriority;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;

final class AgentTownLifeEventPublisher {
    private AgentTownLifeEventPublisher() {
    }

    static void activity(AgentRuntimeEntry entry,
                         Character agent,
                         AgentTownLifeState state,
                         AgentTownLifeActivityEvent.Phase phase,
                         long nowMs) {
        if (entry == null || agent == null || state == null || phase == null) {
            return;
        }
        String correlation = state.decisionCorrelationId().isBlank()
                ? "townlife:" + agent.getId() + ':' + state.sequence()
                : state.decisionCorrelationId();
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .require(state.townMapId());
        AgentSessionEventRuntime.bus(entry).publish(new AgentTownLifeActivityEvent(
                agent.getId(), nowMs, state.townMapId(), profile.profileId(), state.activity(),
                phase, state.venueId(), state.targetCharacterId(), state.decisionSource(), correlation),
                AgentEventPriority.AMBIENT);
        AgentTownLifeMetrics.activity(phase, state.venueId());
    }

    static void encounter(AgentRuntimeEntry entry,
                          Character agent,
                          AgentTownLifeEncounterState.Snapshot encounter,
                          long nowMs) {
        if (entry == null || agent == null || encounter == null
                || encounter.encounterId() == null || encounter.encounterId().isBlank()) {
            return;
        }
        AgentSessionEventRuntime.bus(entry).publish(new AgentTownLifeEncounterEvent(
                agent.getId(), nowMs, agent.getMapId(), encounter.encounterId(), encounter.type(),
                encounter.role(), encounter.phase(), encounter.peerAgentId(),
                encounter.turnOwnerAgentId(), encounter.venueId(), encounter.correlationId()),
                AgentEventPriority.AMBIENT);
        AgentTownLifeMetrics.encounter(encounter.phase());
    }
}
