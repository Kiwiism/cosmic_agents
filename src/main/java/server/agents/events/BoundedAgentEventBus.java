package server.agents.events;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded, session-local event delivery with priority-aware overflow. */
public final class BoundedAgentEventBus implements AgentEventBus {
    public static final int DEFAULT_CAPACITY = 256;
    private final int capacity;
    private final ArrayDeque<AgentEventEnvelope> queue = new ArrayDeque<>();
    private final Map<String, List<AgentEventListener<? super AgentEvent>>> listeners = new HashMap<>();
    private final Set<String> queuedDedupeKeys = new HashSet<>();
    private long nextSequence;
    private long published;
    private long delivered;
    private long dropped;
    private long deduplicated;
    private int highWaterMark;
    private long listenerInvocations;
    private long listenerFailures;
    private long listenerTotalDurationNs;
    private long listenerMaxDurationNs;
    private long queueLatencyTotalNs;
    private long queueLatencyMaxNs;
    private boolean closed;

    public BoundedAgentEventBus() {
        this(DEFAULT_CAPACITY);
    }

    public BoundedAgentEventBus(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Event bus capacity must be positive");
        }
        this.capacity = capacity;
    }

    @Override
    public boolean publish(AgentEvent event) {
        return publish(event, AgentEventPriority.NORMAL);
    }

    @Override
    public synchronized boolean publish(AgentEvent event, AgentEventPriority priority) {
        if (event == null || priority == null || closed) {
            return false;
        }
        String dedupeKey = normalizedDedupeKey(event);
        if (!dedupeKey.isEmpty() && queuedDedupeKeys.contains(dedupeKey)) {
            deduplicated++;
            return false;
        }
        if (queue.size() >= capacity && !makeRoom(priority)) {
            dropped++;
            return false;
        }
        queue.addLast(new AgentEventEnvelope(++nextSequence, event, priority, System.nanoTime()));
        highWaterMark = Math.max(highWaterMark, queue.size());
        if (!dedupeKey.isEmpty()) {
            queuedDedupeKeys.add(dedupeKey);
        }
        published++;
        return true;
    }

    @Override
    public synchronized AgentEventSubscription subscribe(
            String eventType,
            AgentEventListener<? super AgentEvent> listener) {
        if (eventType == null || eventType.isBlank() || listener == null || closed) {
            throw new IllegalArgumentException("Open event bus, event type, and listener are required");
        }
        String normalized = eventType.trim();
        listeners.computeIfAbsent(normalized, ignored -> new ArrayList<>()).add(listener);
        return () -> unsubscribe(normalized, listener);
    }

    @Override
    public int drain(int budget) {
        int deliveredEvents = 0;
        int limit = Math.max(0, budget);
        while (deliveredEvents < limit) {
            AgentEventEnvelope envelope;
            List<AgentEventListener<? super AgentEvent>> targets;
            synchronized (this) {
                envelope = queue.pollFirst();
                if (envelope == null) {
                    break;
                }
                queuedDedupeKeys.remove(normalizedDedupeKey(envelope.event()));
                targets = listenersFor(envelope.event().type());
                long queueLatencyNs = Math.max(0L, System.nanoTime() - envelope.enqueuedAtNanos());
                queueLatencyTotalNs += queueLatencyNs;
                queueLatencyMaxNs = Math.max(queueLatencyMaxNs, queueLatencyNs);
            }
            long invocationCount = 0L;
            long failureCount = 0L;
            long totalDurationNs = 0L;
            long maxDurationNs = 0L;
            for (AgentEventListener<? super AgentEvent> listener : targets) {
                long listenerStartedAt = System.nanoTime();
                try {
                    listener.onAgentEvent(envelope.event());
                } catch (Throwable ignored) {
                    // One projection must not block other consumers or the Agent tick.
                    failureCount++;
                } finally {
                    long durationNs = Math.max(0L, System.nanoTime() - listenerStartedAt);
                    invocationCount++;
                    totalDurationNs += durationNs;
                    maxDurationNs = Math.max(maxDurationNs, durationNs);
                }
            }
            synchronized (this) {
                delivered++;
                listenerInvocations += invocationCount;
                listenerFailures += failureCount;
                listenerTotalDurationNs += totalDurationNs;
                listenerMaxDurationNs = Math.max(listenerMaxDurationNs, maxDurationNs);
            }
            deliveredEvents++;
        }
        return deliveredEvents;
    }

    @Override
    public synchronized AgentEventBusSnapshot snapshot() {
        int subscriptions = listeners.values().stream().mapToInt(List::size).sum();
        return new AgentEventBusSnapshot(capacity, queue.size(), subscriptions,
                published, delivered, dropped, deduplicated, highWaterMark,
                listenerInvocations, listenerFailures, listenerTotalDurationNs,
                listenerMaxDurationNs, queueLatencyTotalNs, queueLatencyMaxNs, closed);
    }

    @Override
    public synchronized void close() {
        closed = true;
        queue.clear();
        listeners.clear();
        queuedDedupeKeys.clear();
    }

    private synchronized void unsubscribe(
            String eventType,
            AgentEventListener<? super AgentEvent> listener) {
        List<AgentEventListener<? super AgentEvent>> registered = listeners.get(eventType);
        if (registered == null) {
            return;
        }
        registered.remove(listener);
        if (registered.isEmpty()) {
            listeners.remove(eventType);
        }
    }

    private List<AgentEventListener<? super AgentEvent>> listenersFor(String type) {
        List<AgentEventListener<? super AgentEvent>> result = new ArrayList<>();
        List<AgentEventListener<? super AgentEvent>> exact = listeners.get(type);
        if (exact != null) {
            result.addAll(exact);
        }
        List<AgentEventListener<? super AgentEvent>> wildcard = listeners.get("*");
        if (wildcard != null) {
            result.addAll(wildcard);
        }
        return List.copyOf(result);
    }

    private boolean makeRoom(AgentEventPriority incoming) {
        if (incoming.ordinal() < AgentEventPriority.IMPORTANT.ordinal()) {
            return false;
        }
        for (AgentEventEnvelope candidate : queue) {
            if (candidate.priority().ordinal() < incoming.ordinal()) {
                queue.remove(candidate);
                queuedDedupeKeys.remove(normalizedDedupeKey(candidate.event()));
                dropped++;
                return true;
            }
        }
        return false;
    }

    private static String normalizedDedupeKey(AgentEvent event) {
        String key = event.dedupeKey();
        return key == null || key.isBlank() ? "" : event.type() + ':' + key.trim();
    }
}
