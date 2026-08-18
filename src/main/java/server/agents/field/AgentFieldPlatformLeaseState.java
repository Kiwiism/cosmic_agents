package server.agents.field;

/** Continuous local-population evidence for one coordinator-issued platform lease. */
final class AgentFieldPlatformLeaseState {
    private long emptySinceMs;

    synchronized boolean releasable(int livePopulation, long nowMs, long releaseDelayMs) {
        if (livePopulation > 0) {
            emptySinceMs = 0L;
            return false;
        }
        if (emptySinceMs == 0L) {
            emptySinceMs = nowMs;
        }
        return nowMs - emptySinceMs >= Math.max(1L, releaseDelayMs);
    }

    synchronized void reset() {
        emptySinceMs = 0L;
    }

    synchronized long emptyForMs(long nowMs) {
        return emptySinceMs == 0L ? 0L : Math.max(0L, nowMs - emptySinceMs);
    }
}
