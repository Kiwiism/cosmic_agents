package server.monitoring;

public record ServerMetricsSnapshot(
        int onlinePlayers,
        int loadedMaps,
        int activeMaps,
        int idleMapCandidates,
        long heapUsedMb,
        long heapMaxMb,
        String dbPool,
        String threadPool,
        String timers,
        ServerLoadLevel loadLevel
) {
    public String compact() {
        return "players=" + onlinePlayers
                + " maps=" + loadedMaps + "/" + activeMaps
                + " idleCandidates=" + idleMapCandidates
                + " heap=" + heapUsedMb + "/" + heapMaxMb + "MB"
                + " " + dbPool
                + " " + threadPool
                + " " + timers
                + " load=" + loadLevel;
    }
}
