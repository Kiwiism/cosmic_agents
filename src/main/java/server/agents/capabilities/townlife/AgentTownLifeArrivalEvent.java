package server.agents.capabilities.townlife;

import server.agents.events.AgentContextualEvent;

/** One-shot arrival fact emitted before an Agent begins a town's arrival route. */
public record AgentTownLifeArrivalEvent(
        int agentId,
        long occurredAtMs,
        int mapId,
        String profileId,
        AgentTownLifeVisitRequest.Purpose purpose,
        String reason) implements AgentContextualEvent {

    public static final String TYPE = "townlife.arrival";

    public AgentTownLifeArrivalEvent {
        if (agentId <= 0 || occurredAtMs < 0 || mapId <= 0
                || profileId == null || profileId.isBlank() || purpose == null) {
            throw new IllegalArgumentException("valid TownLife arrival event fields are required");
        }
        reason = reason == null ? "" : reason.trim();
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
        return "townlife-arrival:" + mapId;
    }
}
