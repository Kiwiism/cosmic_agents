package server.agents.runtime.scheduler;

public record AgentServerHealthSnapshot(
        double processCpuPercent,
        double heapUsedPercent,
        long gcCollectionDeltaMs,
        boolean playerPathHealthy) {
    public AgentServerHealthSnapshot {
        processCpuPercent = clampPercent(processCpuPercent);
        heapUsedPercent = clampPercent(heapUsedPercent);
        gcCollectionDeltaMs = Math.max(0L, gcCollectionDeltaMs);
    }

    public static AgentServerHealthSnapshot healthy() {
        return new AgentServerHealthSnapshot(0.0d, 0.0d, 0L, true);
    }

    private static double clampPercent(double value) {
        if (!Double.isFinite(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(100.0d, value));
    }
}
