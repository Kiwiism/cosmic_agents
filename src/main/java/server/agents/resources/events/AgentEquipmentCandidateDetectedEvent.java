package server.agents.resources.events;

import server.agents.events.AgentEvent;

/** Equipment policy identified an item as a usable upgrade candidate. */
public record AgentEquipmentCandidateDetectedEvent(
        int agentId,
        long occurredAtMs,
        int itemId,
        int sourceCharacterId,
        String objectiveId) implements AgentEvent {
    public static final String TYPE = "equipment.candidate-detected";

    public AgentEquipmentCandidateDetectedEvent {
        if (agentId <= 0 || occurredAtMs < 0 || itemId <= 0 || sourceCharacterId < 0) {
            throw new IllegalArgumentException("Valid equipment candidate context is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String dedupeKey() {
        return String.valueOf(itemId);
    }
}
