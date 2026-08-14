package server.agents.economy.clock;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Monotonic scenario time. It deliberately has no wall-clock dependency. */
public final class LogicalClock {
    private Instant now;

    public LogicalClock(Instant start) {
        this.now = Objects.requireNonNull(start);
    }

    public Instant now() {
        return now;
    }

    public void advanceTo(Instant target) {
        Objects.requireNonNull(target);
        if (target.isBefore(now)) {
            throw new IllegalArgumentException("Logical time cannot move backward");
        }
        now = target;
    }

    public void advanceBy(Duration duration) {
        Objects.requireNonNull(duration);
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Logical duration cannot be negative");
        }
        advanceTo(now.plus(duration));
    }
}
