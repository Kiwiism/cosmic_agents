package server.agents.economy.clock;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Maps monotonic wall-clock elapsed time to a capped one-to-one logical timeline. */
public final class RealtimeEconomyClock {
    private final Instant logicalAnchor;
    private final Instant horizon;
    private final long wallAnchorNanos;

    public RealtimeEconomyClock(Instant logicalAnchor, Instant horizon, long wallAnchorNanos) {
        this.logicalAnchor = Objects.requireNonNull(logicalAnchor, "logicalAnchor");
        this.horizon = Objects.requireNonNull(horizon, "horizon");
        if (horizon.isBefore(logicalAnchor)) throw new IllegalArgumentException("horizon precedes logical anchor");
        this.wallAnchorNanos = wallAnchorNanos;
    }

    public Instant targetAt(long wallNowNanos) {
        if (wallNowNanos < wallAnchorNanos)
            throw new IllegalArgumentException("monotonic wall clock moved backward");
        Instant target = logicalAnchor.plus(Duration.ofNanos(wallNowNanos - wallAnchorNanos));
        return target.isAfter(horizon) ? horizon : target;
    }
}
