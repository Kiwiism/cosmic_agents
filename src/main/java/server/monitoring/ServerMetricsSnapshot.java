package server.monitoring;

public record ServerMetricsSnapshot(
        int onlinePlayers,
        int onlineAgents,
        int loadedMaps,
        int activeMaps,
        int idleMapCandidates,
        long heapUsedMb,
        long heapMaxMb,
        String disk,
        String dbPool,
        String threadPool,
        String timers,
        ServerLoadLevel loadLevel
) {
    public String compact() {
        return "players=" + onlinePlayers
                + " agents=" + onlineAgents
                + " characters=" + (onlinePlayers + onlineAgents)
                + " maps=" + loadedMaps + "/" + activeMaps
                + " idleCandidates=" + idleMapCandidates
                + " heap=" + heapUsedMb + "/" + heapMaxMb + "MB"
                + " " + disk
                + " " + dbPool
                + " " + threadPool
                + " " + timers
                + " load=" + loadLevel;
    }
}
