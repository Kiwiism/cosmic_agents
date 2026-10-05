package server.agents.integration.typesafe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Request, failure, token, latency and cost counters, in total and per judgment kind, plus the
 * observed spend rate since the meter started. Surfaced through the server diagnostics, the
 * {@code !typesafe} command and the periodic usage log.
 */
public final class JevUsageMeter {
    /** Counters for one judgment kind ({@code chat-intent}, {@code name-screen}, ...). */
    public static final class KindUsage {
        private final String kind;
        private final AtomicLong requests = new AtomicLong();
        private final AtomicLong successes = new AtomicLong();
        private final AtomicLong failures = new AtomicLong();
        private final AtomicLong inputTokens = new AtomicLong();
        private final AtomicLong outputTokens = new AtomicLong();
        private final AtomicLong totalLatencyMs = new AtomicLong();
        private final AtomicLong maxLatencyMs = new AtomicLong();

        KindUsage(String kind) {
            this.kind = kind;
        }

        public String kind() {
            return kind;
        }

        public long requests() {
            return requests.get();
        }

        public long successes() {
            return successes.get();
        }

        public long failures() {
            return failures.get();
        }

        public long inputTokens() {
            return inputTokens.get();
        }

        public long outputTokens() {
            return outputTokens.get();
        }

        public long averageLatencyMs() {
            long count = successes.get();
            return count == 0 ? 0L : totalLatencyMs.get() / count;
        }

        public long maxLatencyMs() {
            return maxLatencyMs.get();
        }

        /** Measured input tokens per successful request; the number a projection needs. */
        public long averageInputTokens() {
            long count = successes.get();
            return count == 0 ? 0L : inputTokens.get() / count;
        }

        public double costUsd() {
            return JevCostModel.costUsd(inputTokens.get());
        }

        void recordSuccess(JevResponse response) {
            requests.incrementAndGet();
            successes.incrementAndGet();
            inputTokens.addAndGet(response.inputTokens());
            outputTokens.addAndGet(response.outputTokens());
            totalLatencyMs.addAndGet(response.latencyMs());
            maxLatencyMs.accumulateAndGet(response.latencyMs(), Math::max);
        }

        void recordFailure() {
            requests.incrementAndGet();
            failures.incrementAndGet();
        }

        public String summary() {
            return String.format(Locale.ROOT, "%-15s req=%d ok=%d failed=%d tokens=%d avgTokens=%d avgMs=%d maxMs=%d cost=%s",
                    kind, requests(), successes(), failures(), inputTokens(), averageInputTokens(),
                    averageLatencyMs(), maxLatencyMs(), JevCostModel.money(costUsd()));
        }
    }

    private final long startedAtMs;
    private final KindUsage total = new KindUsage("total");
    private final Map<String, KindUsage> kinds = new ConcurrentHashMap<>();
    private final AtomicLong refusedCircuitOpen = new AtomicLong();
    private final AtomicLong refusedUnconfigured = new AtomicLong();

    public JevUsageMeter() {
        this(System.currentTimeMillis());
    }

    public JevUsageMeter(long startedAtMs) {
        this.startedAtMs = startedAtMs;
    }

    public void recordSuccess(String kind, JevResponse response) {
        total.recordSuccess(response);
        kind(kind).recordSuccess(response);
    }

    public void recordFailure(String kind) {
        total.recordFailure();
        kind(kind).recordFailure();
    }

    public void recordRefusedCircuitOpen() {
        refusedCircuitOpen.incrementAndGet();
    }

    public void recordRefusedUnconfigured() {
        refusedUnconfigured.incrementAndGet();
    }

    public KindUsage total() {
        return total;
    }

    /** Snapshot of every kind seen so far, sorted by name. */
    public List<KindUsage> kinds() {
        List<KindUsage> snapshot = new ArrayList<>(kinds.values());
        snapshot.sort((a, b) -> a.kind().compareTo(b.kind()));
        return snapshot;
    }

    public KindUsage kind(String kind) {
        return kinds.computeIfAbsent(kind == null || kind.isBlank() ? JevRequest.DEFAULT_KIND : kind, KindUsage::new);
    }

    public long requests() {
        return total.requests();
    }

    public long successes() {
        return total.successes();
    }

    public long failures() {
        return total.failures();
    }

    public long inputTokens() {
        return total.inputTokens();
    }

    public long averageLatencyMs() {
        return total.averageLatencyMs();
    }

    public double costUsd() {
        return total.costUsd();
    }

    public long uptimeMs(long nowMs) {
        return Math.max(0L, nowMs - startedAtMs);
    }

    /** Input tokens per hour at the rate observed so far (first minute counts as a full minute). */
    public double observedInputTokensPerHour(long nowMs) {
        double hours = Math.max(60_000L, uptimeMs(nowMs)) / 3_600_000.0d;
        return total.inputTokens() / hours;
    }

    /** Spend per day if the observed rate continued around the clock. */
    public double projectedUsdPerDay(long nowMs) {
        return JevCostModel.costUsd(Math.round(observedInputTokensPerHour(nowMs) * 24.0d));
    }

    public String diagnostics() {
        long now = System.currentTimeMillis();
        return "requests=" + requests()
                + " ok=" + successes()
                + " failed=" + failures()
                + " refusedOpen=" + refusedCircuitOpen.get()
                + " refusedUnconfigured=" + refusedUnconfigured.get()
                + " inputTokens=" + inputTokens()
                + " outputTokens=" + total.outputTokens()
                + " avgLatencyMs=" + averageLatencyMs()
                + " maxLatencyMs=" + total.maxLatencyMs()
                + " cost=" + JevCostModel.money(costUsd())
                + " rate=" + Math.round(observedInputTokensPerHour(now)) + "tok/h"
                + " projected=" + JevCostModel.money(projectedUsdPerDay(now)) + "/day";
    }

    /** Multi-line report for operators: totals, rate and every kind. */
    public List<String> report(long nowMs) {
        List<String> lines = new ArrayList<>();
        long uptimeMinutes = uptimeMs(nowMs) / 60_000L;
        lines.add(String.format(Locale.ROOT,
                "TypeSafe usage over %d min: %d requests (%d ok, %d failed, %d refused), %d input tokens, cost %s",
                uptimeMinutes, requests(), successes(), failures(), refusedCircuitOpen.get() + refusedUnconfigured.get(),
                inputTokens(), JevCostModel.money(costUsd())));
        lines.add(String.format(Locale.ROOT,
                "Observed rate: %d tokens/hour -> %s/day, %s/month if it continues (price %s per million input tokens)",
                Math.round(observedInputTokensPerHour(nowMs)), JevCostModel.money(projectedUsdPerDay(nowMs)),
                JevCostModel.money(projectedUsdPerDay(nowMs) * 30.0d),
                JevCostModel.price(JevCostModel.priceUsdPerMillionInputTokens())));
        for (KindUsage kind : kinds()) {
            lines.add(kind.summary());
        }
        return lines;
    }

    /** One CSV row per kind plus a total row; header via {@link #csvHeader()}. */
    public List<String> csvRows(long nowMs) {
        List<String> rows = new ArrayList<>();
        String stamp = java.time.Instant.ofEpochMilli(nowMs).toString();
        for (KindUsage kind : kinds()) {
            rows.add(csvRow(stamp, kind));
        }
        rows.add(csvRow(stamp, total));
        return rows;
    }

    public static String csvHeader() {
        return "timestamp,kind,requests,ok,failed,input_tokens,output_tokens,avg_input_tokens,avg_latency_ms,max_latency_ms,cost_usd";
    }

    private static String csvRow(String stamp, KindUsage kind) {
        return String.format(Locale.ROOT, "%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%.6f",
                stamp, kind.kind(), kind.requests(), kind.successes(), kind.failures(), kind.inputTokens(),
                kind.outputTokens(), kind.averageInputTokens(), kind.averageLatencyMs(), kind.maxLatencyMs(), kind.costUsd());
    }
}
