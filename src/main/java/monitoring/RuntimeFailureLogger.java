package monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.StackWalker.StackFrame;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Structured replacement for context-free stack traces in legacy runtime code. */
public final class RuntimeFailureLogger {
    private static final Logger log = LoggerFactory.getLogger(RuntimeFailureLogger.class);
    private static final long REPEAT_WINDOW_MS = 5_000;
    private static final StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final ConcurrentHashMap<String, FailureWindow> windows = new ConcurrentHashMap<>();

    private RuntimeFailureLogger() {
    }

    public static void log(Throwable failure) {
        if (failure == null) {
            log.error("Legacy runtime failure was reported without an exception");
            return;
        }
        StackFrame caller = walker.walk(frames -> frames
                .filter(frame -> frame.getDeclaringClass() != RuntimeFailureLogger.class)
                .findFirst()
                .orElse(null));
        String location = caller == null ? "unknown"
                : caller.getClassName() + "#" + caller.getMethodName() + ":" + caller.getLineNumber();
        String key = location + '|' + failure.getClass().getName();
        FailureWindow window = windows.computeIfAbsent(key, ignored -> new FailureWindow());
        long now = System.currentTimeMillis();
        long previous = window.lastLoggedMillis.get();
        if (now - previous < REPEAT_WINDOW_MS || !window.lastLoggedMillis.compareAndSet(previous, now)) {
            window.suppressed.incrementAndGet();
            return;
        }
        long suppressed = window.suppressed.getAndSet(0);
        if (suppressed == 0) {
            log.error("Runtime failure at {}", location, failure);
        } else {
            log.error("Runtime failure at {} ({} equivalent failures since previous report)", location, suppressed, failure);
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().lastLoggedMillis.get() > 60_000);
        }
    }

    public static int trackedFailureKeyCount() {
        return windows.size();
    }

    private static final class FailureWindow {
        private final AtomicLong lastLoggedMillis = new AtomicLong();
        private final AtomicLong suppressed = new AtomicLong();
    }
}
