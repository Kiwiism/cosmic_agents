package server.monitoring;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class MapBroadcastDiagnostics {
    private static final LongAdder totalBroadcasts = new LongAdder();
    private static final LongAdder totalRecipients = new LongAdder();
    private static final LongAdder rangedBroadcasts = new LongAdder();
    private static final LongAdder timedBroadcasts = new LongAdder();
    private static final LongAdder slowBroadcasts = new LongAdder();
    private static final LongAdder totalDurationNanos = new LongAdder();
    private static final AtomicLong maxDurationMs = new AtomicLong();
    private static final AtomicLong lastDurationMs = new AtomicLong();
    private static final AtomicLong lastMapId = new AtomicLong();
    private static final AtomicLong lastRecipients = new AtomicLong();
    private static final AtomicLong maxMapId = new AtomicLong();
    private static final AtomicLong maxRecipients = new AtomicLong();

    private MapBroadcastDiagnostics() {
    }

    public static void recordElapsed(int mapId, int recipients, boolean ranged,
                                     long elapsedNanos, long slowThresholdMs) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        totalBroadcasts.increment();
        totalRecipients.add(recipients);
        if (ranged) {
            rangedBroadcasts.increment();
        }
        timedBroadcasts.increment();
        if (elapsedMs >= slowThresholdMs) {
            slowBroadcasts.increment();
        }
        totalDurationNanos.add(elapsedNanos);
        lastDurationMs.set(elapsedMs);
        lastMapId.set(mapId);
        lastRecipients.set(recipients);
        recordMax(mapId, recipients, elapsedMs);
    }

    /** Adds exact counts and sampled timings retained by a high-frequency physics pass. */
    public static void recordBatch(Batch batch) {
        if (batch == null || batch.broadcasts() <= 0) {
            return;
        }
        totalBroadcasts.add(batch.broadcasts());
        totalRecipients.add(batch.recipients());
        rangedBroadcasts.add(batch.rangedBroadcasts());
        timedBroadcasts.add(batch.timedBroadcasts());
        slowBroadcasts.add(batch.slowBroadcasts());
        totalDurationNanos.add(batch.totalDurationNanos());
        if (batch.timedBroadcasts() > 0) {
            lastDurationMs.set(TimeUnit.NANOSECONDS.toMillis(batch.lastDurationNanos()));
            lastMapId.set(batch.lastMapId());
            lastRecipients.set(batch.lastRecipients());
            recordMax(batch.maxMapId(), batch.maxRecipients(),
                    TimeUnit.NANOSECONDS.toMillis(batch.maxDurationNanos()));
        }
    }

    public static String diagnostics() {
        long broadcasts = totalBroadcasts.sum();
        long timed = timedBroadcasts.sum();
        long avgMs = timed == 0 ? 0
                : TimeUnit.NANOSECONDS.toMillis(totalDurationNanos.sum() / timed);
        long avgRecipients = broadcasts == 0 ? 0 : totalRecipients.sum() / broadcasts;
        return "mapBroadcasts total=" + broadcasts
                + " slow=" + slowBroadcasts.sum()
                + " ranged=" + rangedBroadcasts.sum()
                + " timed=" + timed
                + " avgMs=" + avgMs
                + " avgRecipients=" + avgRecipients
                + " maxMs=" + maxDurationMs.get()
                + " maxMap=" + maxMapId.get()
                + " maxRecipients=" + maxRecipients.get()
                + " lastMs=" + lastDurationMs.get()
                + " lastMap=" + lastMapId.get()
                + " lastRecipients=" + lastRecipients.get();
    }

    private static void recordMax(int mapId, int recipients, long elapsedMs) {
        long current;
        do {
            current = maxDurationMs.get();
            if (elapsedMs <= current) {
                return;
            }
        } while (!maxDurationMs.compareAndSet(current, elapsedMs));
        maxMapId.set(mapId);
        maxRecipients.set(recipients);
    }

    public record Batch(long broadcasts,
                        long recipients,
                        long rangedBroadcasts,
                        long timedBroadcasts,
                        long slowBroadcasts,
                        long totalDurationNanos,
                        long maxDurationNanos,
                        int maxMapId,
                        int maxRecipients,
                        long lastDurationNanos,
                        int lastMapId,
                        int lastRecipients) {
    }
}
