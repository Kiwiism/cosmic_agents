package server.agents.model;

/** Immutable capability-facing view; never retains Cosmic runtime objects. */
public record AgentSnapshot(
        int agentId,
        String name,
        int mapId,
        int level,
        int jobId,
        AgentPosition position,
        boolean alive) {

    public AgentSnapshot {
        if (agentId < 0 || mapId < 0 || level < 0 || name == null || position == null) {
            throw new IllegalArgumentException("Valid Agent identity, map, level, and position are required");
        }
    }

    public static AgentSnapshot unavailable() {
        return new AgentSnapshot(0, "", 0, 0, 0, new AgentPosition(0, 0), false);
    }
}
