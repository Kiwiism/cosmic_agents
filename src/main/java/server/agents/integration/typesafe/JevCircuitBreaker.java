package server.agents.integration.typesafe;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stops calling the API for a cooldown after a run of consecutive failures so a dead or
 * rate-limited endpoint costs nothing but one refused request per judgment.
 */
public final class JevCircuitBreaker {
    private static final int FAILURE_THRESHOLD = config.AgentTuning.intValue(
            "server.agents.integration.typesafe.JevCircuitBreaker.FAILURE_THRESHOLD");
    private static final long OPEN_MS = config.AgentTuning.longValue(
            "server.agents.integration.typesafe.JevCircuitBreaker.OPEN_MS");

    private final int failureThreshold;
    private final long openMs;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openedAtMs = new AtomicLong(Long.MIN_VALUE);

    public JevCircuitBreaker() {
        this(FAILURE_THRESHOLD, OPEN_MS);
    }

    public JevCircuitBreaker(int failureThreshold, long openMs) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openMs = Math.max(0L, openMs);
    }

    public boolean isOpen(long nowMs) {
        long openedAt = openedAtMs.get();
        return openedAt != Long.MIN_VALUE && nowMs - openedAt < openMs;
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        openedAtMs.set(Long.MIN_VALUE);
    }

    public void recordFailure(long nowMs) {
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            openedAtMs.set(nowMs);
            consecutiveFailures.set(0);
        }
    }

    public int consecutiveFailures() {
        return consecutiveFailures.get();
    }
}
