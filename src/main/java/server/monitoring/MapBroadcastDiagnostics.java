package server.monitoring;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class MapBroadcastDiagnostics {
    private static final AtomicLong totalBroadcasts = new AtomicLong();
    private static final AtomicLong totalRecipients = new AtomicLong();
    private static final AtomicLong rangedBroadcasts = new AtomicLong();
    private static final AtomicLong slowBroadcasts = new AtomicLong();
    private static final AtomicLong totalDurationMs = new AtomicLong();
    private static final AtomicLong maxDurationMs = new AtomicLong();
    private static final AtomicLong lastDurationMs = new AtomicLong();
    private static final AtomicLong lastMapId = new AtomicLong();
    private static final AtomicLong lastRecipients = new AtomicLong();
    private static final AtomicLong maxMapId = new AtomicLong();
    private static final AtomicLong maxRecipients = new AtomicLong();

    private MapBroadcastDiagnostics() {
    }

    public static void record(int mapId, int recipients, boolean ranged, long startedNs, long slowThresholdMs) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs);
        totalBroadcasts.incrementAndGet();
        totalRecipients.addAndGet(recipients);
        if (ranged) {
            rangedBroadcasts.incrementAndGet();
        }
        if (elapsedMs >= slowThresholdMs) {
            slowBroadcasts.incrementAndGet();
        }
        totalDurationMs.addAndGet(elapsedMs);
        lastDurationMs.set(elapsedMs);
        lastMapId.set(mapId);
        lastRecipients.set(recipients);
        recordMax(mapId, recipients, elapsedMs);
    }

    public static String diagnostics() {
        long broadcasts = totalBroadcasts.get();
        long avgMs = broadcasts == 0 ? 0 : totalDurationMs.get() / broadcasts;
        long avgRecipients = broadcasts == 0 ? 0 : totalRecipients.get() / broadcasts;
        return "mapBroadcasts total=" + broadcasts
                + " slow=" + slowBroadcasts.get()
                + " ranged=" + rangedBroadcasts.get()
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
}
