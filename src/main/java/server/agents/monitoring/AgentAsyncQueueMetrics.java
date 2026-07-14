package server.agents.monitoring;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/** Lightweight saturation and depth metrics for Agent-owned asynchronous queues. */
public final class AgentAsyncQueueMetrics {
    private static final int DURATION_WINDOW_CAPACITY = 2048;

    public record Snapshot(long submitted,
                           long rejected,
                           long coalesced,
                           int currentDepth,
                           int maxDepth,
                           int capacity,
                           int active,
                           long completed,
                           long failed,
                           long timedOut,
                           long stale,
                           long durationP50Ns,
                           long durationP95Ns,
                           long durationP99Ns,
                           int durationSamples) {
    }

    private static final class MutableMetrics {
        private final LongAdder submitted = new LongAdder();
        private final LongAdder rejected = new LongAdder();
        private final LongAdder coalesced = new LongAdder();
        private final LongAdder completed = new LongAdder();
        private final LongAdder failed = new LongAdder();
        private final LongAdder timedOut = new LongAdder();
        private final LongAdder stale = new LongAdder();
        private final AtomicInteger currentDepth = new AtomicInteger();
        private final AtomicInteger maxDepth = new AtomicInteger();
        private final AtomicInteger capacity = new AtomicInteger();
        private final AtomicInteger active = new AtomicInteger();
        private final DurationWindow durations = new DurationWindow(DURATION_WINDOW_CAPACITY);
    }

    private static final class DurationWindow {
        private final long[] samples;
        private int nextIndex;
        private int size;

        private DurationWindow(int capacity) {
            samples = new long[capacity];
        }

        private synchronized void add(long durationNs) {
            samples[nextIndex] = Math.max(0L, durationNs);
            nextIndex = (nextIndex + 1) % samples.length;
            if (size < samples.length) {
                size++;
            }
        }

        private synchronized DurationPercentiles snapshot() {
            if (size == 0) {
                return new DurationPercentiles(0L, 0L, 0L, 0);
            }
            long[] sorted = Arrays.copyOf(samples, size);
            Arrays.sort(sorted);
            return new DurationPercentiles(
                    percentile(sorted, 50),
                    percentile(sorted, 95),
                    percentile(sorted, 99),
                    size);
        }

        private static long percentile(long[] sorted, int percentile) {
            int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
            return sorted[Math.max(0, index)];
        }
    }

    private record DurationPercentiles(long p50, long p95, long p99, int samples) {
    }

    private static final Map<String, MutableMetrics> METRICS = new ConcurrentHashMap<>();

    private AgentAsyncQueueMetrics() {
    }

    public static void recordSubmitted(String queue, int depth) {
        MutableMetrics metrics = metrics(queue);
        metrics.submitted.increment();
        recordDepth(metrics, depth);
    }

    public static void recordDepth(String queue, int depth) {
        recordDepth(metrics(queue), depth);
    }

    public static void recordRejected(String queue, int depth) {
        MutableMetrics metrics = metrics(queue);
        metrics.rejected.increment();
        recordDepth(metrics, depth);
    }

    public static void recordCoalesced(String queue, int depth) {
        MutableMetrics metrics = metrics(queue);
        metrics.coalesced.increment();
        recordDepth(metrics, depth);
    }

    public static void recordCapacity(String queue, int capacity) {
        metrics(queue).capacity.set(Math.max(0, capacity));
    }

    public static void recordWorkerStarted(String queue, int depth) {
        MutableMetrics metrics = metrics(queue);
        metrics.active.incrementAndGet();
        recordDepth(metrics, depth);
    }

    public static void recordWorkerStopped(String queue, int depth) {
        MutableMetrics metrics = metrics(queue);
        metrics.active.updateAndGet(active -> Math.max(0, active - 1));
        recordDepth(metrics, depth);
    }

    public static void recordCompleted(String queue, long durationNs) {
        MutableMetrics metrics = metrics(queue);
        metrics.completed.increment();
        metrics.durations.add(durationNs);
    }

    public static void recordFailed(String queue, long durationNs) {
        MutableMetrics metrics = metrics(queue);
        metrics.completed.increment();
        metrics.failed.increment();
        metrics.durations.add(durationNs);
    }

    public static void recordTimedOut(String queue, long durationNs) {
        MutableMetrics metrics = metrics(queue);
        metrics.completed.increment();
        metrics.timedOut.increment();
        metrics.durations.add(durationNs);
    }

    public static void recordStale(String queue) {
        metrics(queue).stale.increment();
    }

    public static Snapshot snapshot(String queue) {
        MutableMetrics metrics = metrics(queue);
        return snapshot(metrics);
    }

    public static Map<String, Snapshot> snapshots() {
        Map<String, Snapshot> snapshots = new TreeMap<>();
        METRICS.forEach((queue, metrics) -> snapshots.put(queue, snapshot(metrics)));
        return Collections.unmodifiableMap(snapshots);
    }

    private static Snapshot snapshot(MutableMetrics metrics) {
        DurationPercentiles durations = metrics.durations.snapshot();
        return new Snapshot(
                metrics.submitted.sum(),
                metrics.rejected.sum(),
                metrics.coalesced.sum(),
                metrics.currentDepth.get(),
                metrics.maxDepth.get(),
                metrics.capacity.get(),
                metrics.active.get(),
                metrics.completed.sum(),
                metrics.failed.sum(),
                metrics.timedOut.sum(),
                metrics.stale.sum(),
                durations.p50(),
                durations.p95(),
                durations.p99(),
                durations.samples());
    }

    static void reset() {
        METRICS.clear();
    }

    private static MutableMetrics metrics(String queue) {
        return METRICS.computeIfAbsent(queue, ignored -> new MutableMetrics());
    }

    private static void recordDepth(MutableMetrics metrics, int depth) {
        int boundedDepth = Math.max(0, depth);
        metrics.currentDepth.set(boundedDepth);
        metrics.maxDepth.accumulateAndGet(boundedDepth, Math::max);
    }
}
