package server.agents.economy.clock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

/** Stable, checkpointable scheduled-event queue. */
public final class LogicalEventQueue {
    private final PriorityQueue<ScheduledEconomyEvent> events = new PriorityQueue<>();
    private long nextSequence;

    public ScheduledEconomyEvent schedule(Instant dueAt, String kind, String subjectId,
                                          java.util.Map<String, String> parameters) {
        ScheduledEconomyEvent event = new ScheduledEconomyEvent(
                dueAt, nextSequence++, kind, subjectId, parameters);
        events.add(event);
        return event;
    }

    public void restore(Collection<ScheduledEconomyEvent> restored) {
        events.clear();
        events.addAll(restored);
        nextSequence = restored.stream().mapToLong(ScheduledEconomyEvent::sequence).max().orElse(-1) + 1;
    }

    public Optional<ScheduledEconomyEvent> peek() {
        return Optional.ofNullable(events.peek());
    }

    public Optional<ScheduledEconomyEvent> poll() {
        return Optional.ofNullable(events.poll());
    }

    public List<ScheduledEconomyEvent> snapshot() {
        ArrayList<ScheduledEconomyEvent> snapshot = new ArrayList<>(events);
        snapshot.sort(null);
        return List.copyOf(snapshot);
    }

    public int size() {
        return events.size();
    }
}
