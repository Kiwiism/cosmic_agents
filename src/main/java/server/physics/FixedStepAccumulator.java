package server.physics;

/** Deterministic bounded fixed-step accumulator used by the channel simulator. */
public final class FixedStepAccumulator {
    private final long stepNanos;
    private long accumulatedNanos;

    public FixedStepAccumulator(long stepMillis) {
        if (stepMillis <= 0) {
            throw new IllegalArgumentException("stepMillis must be positive");
        }
        stepNanos = stepMillis * 1_000_000L;
    }

    public StepBatch accumulate(long elapsedNanos, int maximumSteps) {
        if (elapsedNanos < 0 || maximumSteps <= 0) {
            throw new IllegalArgumentException("elapsedNanos and maximumSteps must be non-negative/positive");
        }
        long maximum = stepNanos * maximumSteps;
        long before = accumulatedNanos;
        accumulatedNanos = Math.min(maximum, accumulatedNanos + elapsedNanos);
        boolean capped = before + elapsedNanos > maximum;
        int steps = (int) Math.min(maximumSteps, accumulatedNanos / stepNanos);
        accumulatedNanos -= steps * stepNanos;
        return new StepBatch(steps, capped, accumulatedNanos);
    }

    public record StepBatch(int steps, boolean capped, long leftoverNanos) {
    }
}
