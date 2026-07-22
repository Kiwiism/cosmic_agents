package server.agents.operations.events;

import server.agents.events.AgentContextualEvent;

/** Damage outcome emitted after the authoritative attack route accepted an Agent attack. */
public record AgentAttackResolvedEvent(int agentId, long occurredAtMs, int mapId,
                                       int targetCount, int hitLines, int missLines,
                                       String objectiveId) implements AgentContextualEvent {
    public static final String TYPE = "combat.attack-resolved";

    public AgentAttackResolvedEvent {
        if (agentId <= 0 || occurredAtMs < 0 || mapId < 0 || targetCount < 1
                || hitLines < 0 || missLines < 0) throw new IllegalArgumentException("valid attack outcome is required");
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    public AgentAttackResolvedEvent(int agentId, long occurredAtMs, int mapId,
                                    int targetCount, int hitLines, int missLines) {
        this(agentId, occurredAtMs, mapId, targetCount, hitLines, missLines, "");
    }

    @Override public String type() { return TYPE; }
}
