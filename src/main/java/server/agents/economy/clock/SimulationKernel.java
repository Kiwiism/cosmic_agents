package server.agents.economy.clock;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Advances directly between meaningful events, bounded by a per-batch safety limit. */
public final class SimulationKernel {
    private final LogicalClock clock;
    private final LogicalEventQueue queue;
    private final int maxEventsPerBatch;

    public SimulationKernel(LogicalClock clock, LogicalEventQueue queue, int maxEventsPerBatch) {
        this.clock = Objects.requireNonNull(clock);
        this.queue = Objects.requireNonNull(queue);
        if (maxEventsPerBatch <= 0) throw new IllegalArgumentException("maxEventsPerBatch must be positive");
        this.maxEventsPerBatch = maxEventsPerBatch;
    }

    public AdvanceResult advanceUntil(Instant target, Consumer<ScheduledEconomyEvent> handler) {
        return advanceUntil(target, handler, () -> false);
    }

    public AdvanceResult advanceUntil(Instant target, Consumer<ScheduledEconomyEvent> handler,
                                      BooleanSupplier stopRequested) {
        return advance(target, handler, stopRequested, true);
    }

    /** Advances the clock to target while leaving events timestamped exactly at target queued. */
    public AdvanceResult advanceUntilExclusive(Instant target, Consumer<ScheduledEconomyEvent> handler,
                                               BooleanSupplier stopRequested) {
        return advance(target, handler, stopRequested, false);
    }

    private AdvanceResult advance(Instant target, Consumer<ScheduledEconomyEvent> handler,
                                  BooleanSupplier stopRequested, boolean inclusive) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(handler);
        if (target.isBefore(clock.now())) throw new IllegalArgumentException("Cannot rewind a run");
        int processed = 0;
        boolean externallyStopped = false;
        while (processed < maxEventsPerBatch) {
            ScheduledEconomyEvent next = queue.peek().orElse(null);
            if (next == null || next.dueAt().isAfter(target)
                    || (!inclusive && next.dueAt().equals(target))) break;
            queue.poll();
            clock.advanceTo(next.dueAt());
            handler.accept(next);
            processed++;
            if (stopRequested.getAsBoolean()) { externallyStopped = true; break; }
        }
        boolean batchLimitReached = processed == maxEventsPerBatch
                && queue.peek().map(event -> !event.dueAt().isAfter(target)
                        && (inclusive || !event.dueAt().equals(target))).orElse(false);
        if (!batchLimitReached && !externallyStopped) clock.advanceTo(target);
        return new AdvanceResult(clock.now(), processed, batchLimitReached, externallyStopped, queue.size());
    }

    public record AdvanceResult(Instant reachedAt, int processedEvents,
                                boolean batchLimitReached, boolean externallyStopped,
                                int queuedEvents) { }
}
