package server.agents.population;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class AgentPopulationMetrics {
    public record Snapshot(int target, int live, int managed, long failures,
                           long lastSweepStartedMs, long lastSweepDurationMs,
                           long reconciliationLagMs, int queuedCallbacks) {
    }

    private final AtomicInteger target = new AtomicInteger();
    private final AtomicInteger live = new AtomicInteger();
    private final AtomicInteger managed = new AtomicInteger();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong lastSweepStartedMs = new AtomicLong();
    private final AtomicLong lastSweepDurationMs = new AtomicLong();
    private final AtomicLong reconciliationLagMs = new AtomicLong();
    private final AtomicInteger queuedCallbacks = new AtomicInteger();

    void recordCensus(int targetCount, int liveCount, int managedCount) {
        target.set(targetCount);
        live.set(liveCount);
        managed.set(managedCount);
    }

    void recordFailure() {
        failures.incrementAndGet();
    }

    void recordSweep(long startedMs, long durationMs) {
        lastSweepStartedMs.set(startedMs);
        lastSweepDurationMs.set(durationMs);
    }

    void recordExpectedSweep(long expectedMs, long actualMs) {
        reconciliationLagMs.set(Math.max(0L, actualMs - expectedMs));
    }

    void setQueuedCallbacks(int count) {
        queuedCallbacks.set(Math.max(0, count));
    }

    public Snapshot snapshot() {
        return new Snapshot(target.get(), live.get(), managed.get(), failures.get(),
                lastSweepStartedMs.get(), lastSweepDurationMs.get(),
                reconciliationLagMs.get(), queuedCallbacks.get());
    }
}
