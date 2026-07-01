package server.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public final class SlowOperationLogger {
    private static final Logger log = LoggerFactory.getLogger(SlowOperationLogger.class);

    private SlowOperationLogger() {
    }

    public static long start() {
        return System.nanoTime();
    }

    public static void warnIfSlow(String label, long startedNs, long thresholdMs) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs);
        if (elapsedMs >= thresholdMs) {
            log.warn("Slow operation {} took {} ms", label, elapsedMs);
        }
    }
}
