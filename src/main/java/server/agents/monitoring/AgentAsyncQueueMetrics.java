package server.agents.monitoring;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/** Lightweight saturation and depth metrics for Agent-owned asynchronous queues. */
public final class AgentAsyncQueueMetrics {
    public record Snapshot(long submitted, long rejected, long coalesced, int currentDepth, int maxDepth) {
    }

    private static final class MutableMetrics {
        private final LongAdder submitted = new LongAdder();
        private final LongAdder rejected = new LongAdder();
        private final LongAdder coalesced = new LongAdder();
        private final AtomicInteger currentDepth = new AtomicInteger();
        private final AtomicInteger maxDepth = new AtomicInteger();
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

    public static Snapshot snapshot(String queue) {
        MutableMetrics metrics = metrics(queue);
        return new Snapshot(
                metrics.submitted.sum(),
                metrics.rejected.sum(),
                metrics.coalesced.sum(),
                metrics.currentDepth.get(),
                metrics.maxDepth.get());
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
