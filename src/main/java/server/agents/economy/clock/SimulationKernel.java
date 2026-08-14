package server.agents.economy.clock;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

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
        Objects.requireNonNull(target);
        Objects.requireNonNull(handler);
        if (target.isBefore(clock.now())) throw new IllegalArgumentException("Cannot rewind a run");
        int processed = 0;
        while (processed < maxEventsPerBatch) {
            ScheduledEconomyEvent next = queue.peek().orElse(null);
            if (next == null || next.dueAt().isAfter(target)) break;
            queue.poll();
            clock.advanceTo(next.dueAt());
            handler.accept(next);
            processed++;
        }
        boolean batchLimitReached = processed == maxEventsPerBatch
                && queue.peek().map(event -> !event.dueAt().isAfter(target)).orElse(false);
        if (!batchLimitReached) clock.advanceTo(target);
        return new AdvanceResult(clock.now(), processed, batchLimitReached, queue.size());
    }

    public record AdvanceResult(Instant reachedAt, int processedEvents,
                                boolean batchLimitReached, int queuedEvents) { }
}
