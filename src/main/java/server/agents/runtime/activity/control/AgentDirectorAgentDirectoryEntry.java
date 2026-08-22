package server.agents.runtime.activity.control;

/** Lightweight roster row; full resources are loaded only for a selected live Agent. */
public record AgentDirectorAgentDirectoryEntry(
        int characterId,
        String name,
        int level,
        int jobId,
        int mapId,
        boolean online,
        boolean runtimeActive) {
    public AgentDirectorAgentDirectoryEntry {
        name = name == null ? "" : name.trim();
        if (characterId <= 0 || name.isEmpty() || level <= 0 || jobId < 0 || mapId < 0) {
            throw new IllegalArgumentException("valid Director roster row is required");
        }
    }
}
