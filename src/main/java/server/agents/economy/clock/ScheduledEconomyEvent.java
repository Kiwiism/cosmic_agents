package server.agents.economy.clock;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Serializable queue entry; handlers are resolved by kind rather than captured as lambdas. */
public record ScheduledEconomyEvent(
        Instant dueAt,
        long sequence,
        String kind,
        String subjectId,
        Map<String, String> parameters
) implements Comparable<ScheduledEconomyEvent> {
    public ScheduledEconomyEvent {
        Objects.requireNonNull(dueAt);
        if (sequence < 0) throw new IllegalArgumentException("sequence must be non-negative");
        if (kind == null || kind.isBlank()) throw new IllegalArgumentException("kind is required");
        subjectId = subjectId == null ? "" : subjectId;
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    @Override
    public int compareTo(ScheduledEconomyEvent other) {
        int time = dueAt.compareTo(other.dueAt);
        return time != 0 ? time : Long.compare(sequence, other.sequence);
    }
}
