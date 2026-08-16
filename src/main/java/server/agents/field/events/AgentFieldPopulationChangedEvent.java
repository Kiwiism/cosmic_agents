package server.agents.field.events;

import server.agents.events.AgentContextualEvent;

/** Map-session membership change used for formation rebalance narration and metrics. */
public record AgentFieldPopulationChangedEvent(
        int agentId,
        long occurredAtMs,
        int mapId,
        String sessionId,
        Change change,
        int population,
        String reason,
        String objectiveId) implements AgentContextualEvent {
    public static final String TYPE = "field.population-changed";

    public AgentFieldPopulationChangedEvent {
        sessionId = normalize(sessionId);
        reason = normalize(reason);
        objectiveId = normalize(objectiveId);
        if (agentId <= 0 || occurredAtMs < 0L || mapId <= 0 || sessionId.isEmpty()
                || change == null || population < 0) {
            throw new IllegalArgumentException("valid field population event fields are required");
        }
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String dedupeKey() {
        return sessionId + ":population:" + change + ':' + population;
    }

    public enum Change { JOINED, LEFT, REBALANCED }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
