package server.agents.operations.events;

import server.agents.events.AgentContextualEvent;

/** Lifecycle fact for a reversible crowd respite without changing objective ownership. */
public record AgentCrowdRespiteEvent(int agentId, long occurredAtMs, int mapId,
                                    Stage stage, String objectiveId) implements AgentContextualEvent {
    public static final String TYPE = "behavior.crowd-respite";

    public enum Stage { STARTED, SETTLED, RESUMED }

    public AgentCrowdRespiteEvent {
        if (agentId <= 0 || occurredAtMs < 0 || mapId < 0 || stage == null) {
            throw new IllegalArgumentException("valid crowd respite event is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override public String type() { return TYPE; }
}
