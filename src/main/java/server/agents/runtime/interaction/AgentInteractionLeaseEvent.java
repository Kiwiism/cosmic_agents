package server.agents.runtime.interaction;

import server.agents.events.AgentContextualEvent;

/** Observable lifecycle fact for a bounded nested Agent interaction. */
public record AgentInteractionLeaseEvent(
        int agentId,
        long occurredAtMs,
        String interactionId,
        AgentInteractionLeaseState.Type interactionType,
        int participantCharacterId,
        String townLifeSessionId,
        Phase phase,
        String reason) implements AgentContextualEvent {

    public static final String TYPE = "agent.interaction-lease";

    public AgentInteractionLeaseEvent {
        interactionId = normalize(interactionId);
        townLifeSessionId = normalize(townLifeSessionId);
        reason = normalize(reason);
        if (agentId <= 0 || occurredAtMs < 0L || interactionId.isBlank()
                || interactionType == null || phase == null) {
            throw new IllegalArgumentException("valid interaction lease event is required");
        }
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
        return interactionId + ':' + phase;
    }

    public enum Phase {
        STARTED,
        COMPLETED,
        TIMED_OUT,
        CANCELLED
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
