package server.agents.monitoring;

import java.util.Arrays;

/** Fixed-capacity rolling sample window used by scheduler diagnostics. */
final class AgentSchedulerRollingWindow {
    record Percentiles(long p50, long p95, long p99, int sampleCount) {
    }

    private final long[] samples;
    private int nextIndex;
    private int size;

    AgentSchedulerRollingWindow(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Rolling window capacity must be positive");
        }
        samples = new long[capacity];
    }

    synchronized void add(long sample) {
        samples[nextIndex] = Math.max(0L, sample);
        nextIndex = (nextIndex + 1) % samples.length;
        if (size < samples.length) {
            size++;
        }
    }

    synchronized Percentiles percentiles() {
        if (size == 0) {
            return new Percentiles(0L, 0L, 0L, 0);
        }
        long[] sorted = Arrays.copyOf(samples, size);
        Arrays.sort(sorted);
        return new Percentiles(
                percentile(sorted, 50),
                percentile(sorted, 95),
                percentile(sorted, 99),
                size);
    }

    synchronized void reset() {
        Arrays.fill(samples, 0L);
        nextIndex = 0;
        size = 0;
    }

    private static long percentile(long[] sorted, int percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, index)];
    }
}
