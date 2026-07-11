package server.agents.population;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class AgentPopulationMetrics {
    public record Snapshot(int target, int live, int managed, long failures,
                           long lastSweepStartedMs, long lastSweepDurationMs) {
    }

    private final AtomicInteger target = new AtomicInteger();
    private final AtomicInteger live = new AtomicInteger();
    private final AtomicInteger managed = new AtomicInteger();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong lastSweepStartedMs = new AtomicLong();
    private final AtomicLong lastSweepDurationMs = new AtomicLong();

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

    public Snapshot snapshot() {
        return new Snapshot(target.get(), live.get(), managed.get(), failures.get(),
                lastSweepStartedMs.get(), lastSweepDurationMs.get());
    }
}
