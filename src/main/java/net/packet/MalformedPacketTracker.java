package net.packet;

import java.util.concurrent.TimeUnit;

public final class MalformedPacketTracker {
    private static final int DEFAULT_LIMIT = 3;
    private static final long DEFAULT_WINDOW_MS = TimeUnit.SECONDS.toMillis(10);

    private final int limit;
    private final long windowMs;
    private long windowStartedMs;
    private int violations;

    public MalformedPacketTracker() {
        this(DEFAULT_LIMIT, DEFAULT_WINDOW_MS);
    }

    MalformedPacketTracker(int limit, long windowMs) {
        this.limit = limit;
        this.windowMs = windowMs;
    }

    public synchronized boolean record(long nowMs) {
        if (windowStartedMs == 0 || nowMs - windowStartedMs > windowMs) {
            windowStartedMs = nowMs;
            violations = 0;
        }
        return ++violations >= limit;
    }
}
