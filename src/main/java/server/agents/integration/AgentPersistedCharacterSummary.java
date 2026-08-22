package server.agents.integration;

/** Minimal offline facts for a Director roster; loading gameplay state remains explicit. */
public record AgentPersistedCharacterSummary(
        int characterId,
        String name,
        int accountId,
        int level,
        int jobId,
        int mapId) {
    public AgentPersistedCharacterSummary {
        name = name == null ? "" : name.trim();
        if (characterId <= 0 || name.isEmpty() || accountId <= 0
                || level <= 0 || jobId < 0 || mapId < 0) {
            throw new IllegalArgumentException("valid persisted Agent summary is required");
        }
    }
}
