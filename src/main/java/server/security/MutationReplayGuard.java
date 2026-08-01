package server.security;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/** Short replay window for one-shot mutations identified by a durable resource id. */
public final class MutationReplayGuard {
    private static final long WINDOW_MILLIS = Duration.ofSeconds(2).toMillis();
    private static final int MAX_ENTRIES = 20_000;
    private static final ConcurrentHashMap<String, Long> recent = new ConcurrentHashMap<>();

    private MutationReplayGuard() {
    }

    public static boolean acquire(int characterId, String family, long resourceId) {
        long now = System.currentTimeMillis();
        String key = characterId + ":" + family + ":" + resourceId;
        Long previous = recent.put(key, now);
        if (recent.size() > MAX_ENTRIES) {
            recent.entrySet().removeIf(entry -> now - entry.getValue() > WINDOW_MILLIS);
            int overflow = recent.size() - MAX_ENTRIES;
            for (var entry : recent.entrySet()) {
                if (overflow-- <= 0) {
                    break;
                }
                recent.remove(entry.getKey(), entry.getValue());
            }
        }
        return previous == null || now - previous > WINDOW_MILLIS;
    }

    static void clearForTesting() {
        recent.clear();
    }
}
