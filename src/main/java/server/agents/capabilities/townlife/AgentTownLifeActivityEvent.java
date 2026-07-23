package server.agents.capabilities.townlife;

import server.agents.events.AgentContextualEvent;

/** Immutable TownLife activity fact for presentation, memory, metrics, and future controllers. */
public record AgentTownLifeActivityEvent(
        int agentId,
        long occurredAtMs,
        int mapId,
        String profileId,
        AgentTownLifeState.Activity activity,
        Phase phase,
        String venueId,
        int peerAgentId,
        String decisionSource,
        String correlationId) implements AgentContextualEvent {

    public static final String TYPE = "townlife.activity";

    public AgentTownLifeActivityEvent {
        if (agentId <= 0 || occurredAtMs < 0 || mapId <= 0 || profileId == null
                || profileId.isBlank() || activity == null || phase == null) {
            throw new IllegalArgumentException("valid TownLife activity event fields are required");
        }
        venueId = normalize(venueId);
        decisionSource = normalize(decisionSource);
        correlationId = normalize(correlationId);
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
        return correlationId + ':' + phase;
    }

    public enum Phase {
        SELECTED,
        APPROACHING,
        ARRIVED,
        ORIENTING,
        OPENING,
        PERFORMING,
        REACTING,
        CLOSING,
        COMPLETED,
        ABANDONED
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
