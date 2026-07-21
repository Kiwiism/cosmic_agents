package server.agents.capabilities.presentation;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/** Fixed-cardinality counters; no per-Agent labels are retained. */
public final class AgentPresentationTelemetry {
    public record Snapshot(
            long triggers,
            long scheduled,
            long executed,
            long observerSuppressed,
            long unsafeBlocked,
            long coalesced,
            Map<AgentPresentationIntent, Long> executedByIntent) {
    }

    private static final LongAdder TRIGGERS = new LongAdder();
    private static final LongAdder SCHEDULED = new LongAdder();
    private static final LongAdder EXECUTED = new LongAdder();
    private static final LongAdder OBSERVER_SUPPRESSED = new LongAdder();
    private static final LongAdder UNSAFE_BLOCKED = new LongAdder();
    private static final LongAdder COALESCED = new LongAdder();
    private static final EnumMap<AgentPresentationIntent, LongAdder> EXECUTED_BY_INTENT =
            new EnumMap<>(AgentPresentationIntent.class);

    static {
        for (AgentPresentationIntent intent : AgentPresentationIntent.values()) {
            EXECUTED_BY_INTENT.put(intent, new LongAdder());
        }
    }

    private AgentPresentationTelemetry() {
    }

    public static void recordTrigger() {
        TRIGGERS.increment();
    }

    public static void recordScheduled() {
        SCHEDULED.increment();
    }

    public static void recordExecuted(AgentPresentationIntent intent) {
        EXECUTED.increment();
        EXECUTED_BY_INTENT.get(intent).increment();
    }

    public static void recordObserverSuppressed() {
        OBSERVER_SUPPRESSED.increment();
    }

    public static void recordUnsafeBlocked() {
        UNSAFE_BLOCKED.increment();
    }

    public static void recordCoalesced() {
        COALESCED.increment();
    }

    public static Snapshot snapshot() {
        EnumMap<AgentPresentationIntent, Long> intents = new EnumMap<>(AgentPresentationIntent.class);
        EXECUTED_BY_INTENT.forEach((intent, count) -> intents.put(intent, count.sum()));
        return new Snapshot(TRIGGERS.sum(), SCHEDULED.sum(), EXECUTED.sum(),
                OBSERVER_SUPPRESSED.sum(), UNSAFE_BLOCKED.sum(), COALESCED.sum(),
                Map.copyOf(intents));
    }

    static void resetForTests() {
        TRIGGERS.reset();
        SCHEDULED.reset();
        EXECUTED.reset();
        OBSERVER_SUPPRESSED.reset();
        UNSAFE_BLOCKED.reset();
        COALESCED.reset();
        EXECUTED_BY_INTENT.values().forEach(LongAdder::reset);
    }
}
