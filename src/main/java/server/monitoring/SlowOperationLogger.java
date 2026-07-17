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
        warnIfSlowElapsed(label, System.nanoTime() - startedNs, thresholdMs);
    }

    public static boolean isSlow(long elapsedNs, long thresholdMs) {
        return elapsedNs >= TimeUnit.MILLISECONDS.toNanos(thresholdMs);
    }

    public static void warnIfSlowElapsed(String label, long elapsedNs, long thresholdMs) {
        if (isSlow(elapsedNs, thresholdMs)) {
            log.warn("Slow operation {} took {} ms", label,
                    TimeUnit.NANOSECONDS.toMillis(elapsedNs));
        }
    }

    public static String diagnostics() {
        return "thresholds login=1000ms loginState=1000ms characterLoad=5000ms characterSave=1000ms"
                + " characterDelete=1000/5000ms startupDb=5000ms mapUpdate=250ms mapBroadcast=100ms";
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
