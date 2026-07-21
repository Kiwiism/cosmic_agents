package server.agents.resources.events;

import server.agents.events.AgentContextualEvent;

/** Scroll outcome for an Agent-owned scrolling attempt. */
public record AgentScrollResolvedEvent(
        int agentId,
        long occurredAtMs,
        int scrollItemId,
        String result,
        int mapId,
        String objectiveId) implements AgentContextualEvent {
    public static final String TYPE = "equipment.scroll-resolved";

    public AgentScrollResolvedEvent {
        if (agentId <= 0 || occurredAtMs < 0 || scrollItemId <= 0 || result == null
                || result.isBlank() || mapId < -1) {
            throw new IllegalArgumentException("Valid scroll outcome context is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }
}
