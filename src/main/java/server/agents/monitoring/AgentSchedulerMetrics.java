package server.agents.monitoring;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class AgentSchedulerMetrics {
    public record Snapshot(long cycles,
                           long updatedAgents,
                           long skippedAgents,
                           long failedAgents,
                           long slowAgents,
                           long totalCycleNs,
                           long maxCycleNs,
                           long totalQueueLagMs,
                           long maxQueueLagMs) {
    }

    private static final LongAdder CYCLES = new LongAdder();
    private static final LongAdder UPDATED = new LongAdder();
    private static final LongAdder SKIPPED = new LongAdder();
    private static final LongAdder FAILED = new LongAdder();
    private static final LongAdder SLOW = new LongAdder();
    private static final LongAdder TOTAL_CYCLE_NS = new LongAdder();
    private static final AtomicLong MAX_CYCLE_NS = new AtomicLong();
    private static final LongAdder TOTAL_QUEUE_LAG_MS = new LongAdder();
    private static final AtomicLong MAX_QUEUE_LAG_MS = new AtomicLong();

    private AgentSchedulerMetrics() {
    }

    public static void recordCycle(long elapsedNs) {
        CYCLES.increment();
        TOTAL_CYCLE_NS.add(Math.max(0L, elapsedNs));
        MAX_CYCLE_NS.accumulateAndGet(Math.max(0L, elapsedNs), Math::max);
    }

    public static void recordUpdated(long queueLagMs, boolean slow) {
        UPDATED.increment();
        long lag = Math.max(0L, queueLagMs);
        TOTAL_QUEUE_LAG_MS.add(lag);
        MAX_QUEUE_LAG_MS.accumulateAndGet(lag, Math::max);
        if (slow) {
            SLOW.increment();
        }
    }

    public static void recordSkipped(long count) {
        if (count > 0) {
            SKIPPED.add(count);
        }
    }

    public static void recordFailure() {
        FAILED.increment();
    }

    public static Snapshot snapshot() {
        return new Snapshot(
                CYCLES.sum(), UPDATED.sum(), SKIPPED.sum(), FAILED.sum(), SLOW.sum(),
                TOTAL_CYCLE_NS.sum(), MAX_CYCLE_NS.get(),
                TOTAL_QUEUE_LAG_MS.sum(), MAX_QUEUE_LAG_MS.get());
    }

    static void reset() {
        CYCLES.reset();
        UPDATED.reset();
        SKIPPED.reset();
        FAILED.reset();
        SLOW.reset();
        TOTAL_CYCLE_NS.reset();
        MAX_CYCLE_NS.set(0L);
        TOTAL_QUEUE_LAG_MS.reset();
        MAX_QUEUE_LAG_MS.set(0L);
    }
}
