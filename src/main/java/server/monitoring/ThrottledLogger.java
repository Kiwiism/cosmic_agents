package server.monitoring;

import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ThrottledLogger {
    private static final int BURST_LIMIT = 3;
    private static final long SUMMARY_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5);
    private static final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    private ThrottledLogger() {
    }

    public static void warn(String key, Logger log, String message, Throwable cause, Object... args) {
        log(key, log, false, message, cause, args);
    }

    public static void error(String key, Logger log, String message, Throwable cause, Object... args) {
        log(key, log, true, message, cause, args);
    }

    private static void log(String key, Logger log, boolean error, String message, Throwable cause, Object... args) {
        State state = states.computeIfAbsent(key, ignored -> new State());
        int count = state.count.incrementAndGet();
        long now = System.currentTimeMillis();
        boolean withinBurst = count <= BURST_LIMIT;
        boolean summaryDue = now - state.lastSummaryMs >= SUMMARY_INTERVAL_MS;

        if (withinBurst) {
            if (error) {
                log.error(message, appendCause(args, cause));
            } else {
                log.warn(message, appendCause(args, cause));
            }
            return;
        }

        if (summaryDue) {
            state.lastSummaryMs = now;
            if (error) {
                log.error("Repeated exception key={} count={} message=\"{}\". Further repeats are throttled.", key, count, message, cause);
            } else {
                log.warn("Repeated exception key={} count={} message=\"{}\". Further repeats are throttled.", key, count, message, cause);
            }
        }
    }

    private static Object[] appendCause(Object[] args, Throwable cause) {
        Object[] withCause = new Object[args.length + 1];
        System.arraycopy(args, 0, withCause, 0, args.length);
        withCause[args.length] = cause;
        return withCause;
    }

    private static final class State {
        private final AtomicInteger count = new AtomicInteger();
        private volatile long lastSummaryMs;
    }
}
