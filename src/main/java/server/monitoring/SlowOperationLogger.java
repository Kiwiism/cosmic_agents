package server.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

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

    public static void run(String label, long thresholdMs, Runnable action) {
        long startedNs = start();
        try {
            action.run();
        } finally {
            warnIfSlow(label, startedNs, thresholdMs);
        }
    }

    public static <T> T get(String label, long thresholdMs, Supplier<T> action) {
        long startedNs = start();
        try {
            return action.get();
        } finally {
            warnIfSlow(label, startedNs, thresholdMs);
        }
    }
}
