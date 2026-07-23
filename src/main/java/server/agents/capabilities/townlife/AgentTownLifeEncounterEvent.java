package server.agents.capabilities.townlife;

import server.agents.events.AgentContextualEvent;

/** Shared social encounter lifecycle projected separately into each participant's session. */
public record AgentTownLifeEncounterEvent(
        int agentId,
        long occurredAtMs,
        int mapId,
        String encounterId,
        AgentTownLifeEncounterState.Type encounterType,
        AgentTownLifeEncounterState.Role participantRole,
        AgentTownLifeEncounterState.Phase phase,
        int peerAgentId,
        int turnOwnerAgentId,
        String venueId,
        String correlationId) implements AgentContextualEvent {

    public static final String TYPE = "townlife.encounter";

    public AgentTownLifeEncounterEvent {
        if (agentId <= 0 || occurredAtMs < 0 || mapId <= 0 || encounterId == null
                || encounterId.isBlank() || encounterType == null || participantRole == null
                || phase == null || peerAgentId <= 0 || turnOwnerAgentId <= 0) {
            throw new IllegalArgumentException("valid TownLife encounter event fields are required");
        }
        venueId = venueId == null ? "" : venueId.trim();
        correlationId = correlationId == null || correlationId.isBlank()
                ? encounterId : correlationId.trim();
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String objectiveId() {
        return "";
    }

    @Override
    public String dedupeKey() {
        return encounterId + ':' + agentId + ':' + phase;
    }
}
