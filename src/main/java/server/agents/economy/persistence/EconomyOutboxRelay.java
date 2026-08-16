package server.agents.economy.persistence;

import java.util.Objects;

/** At-least-once relay; the PostgreSQL sink makes retries harmless. */
public final class EconomyOutboxRelay {
    private final CosmicOutboxSource source;
    private final CosmicOutboxSink sink;

    public EconomyOutboxRelay(CosmicOutboxSource source, CosmicOutboxSink sink) {
        this.source = Objects.requireNonNull(source);
        this.sink = Objects.requireNonNull(sink);
    }

    public Result relay(int limit) {
        int delivered = 0;
        int failed = 0;
        for (CosmicOutboxRecord record : source.pending(limit)) {
            try {
                sink.accept(record);
                source.markPublished(record.outboxId());
                delivered++;
            } catch (RuntimeException failure) {
                source.markFailed(record.outboxId(), failure.getMessage());
                failed++;
            }
        }
        return new Result(delivered, failed);
    }

    public record Result(int delivered, int failed) { }
}
