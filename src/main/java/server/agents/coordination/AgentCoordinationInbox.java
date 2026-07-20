package server.agents.coordination;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Lock-contained bounded route queue. */
final class AgentCoordinationInbox {
    private final int capacity;
    private final ArrayDeque<AgentCoordinationEnvelope> queue = new ArrayDeque<>();

    AgentCoordinationInbox(int capacity) {
        this.capacity = capacity;
    }

    synchronized OfferResult offer(AgentCoordinationEnvelope envelope, long nowMillis) {
        int expired = discardExpired(nowMillis);
        if (queue.size() >= capacity) {
            return new OfferResult(false, expired);
        }
        queue.addLast(envelope);
        return new OfferResult(true, expired);
    }

    synchronized DrainResult drain(int limit, long nowMillis) {
        int expired = discardExpired(nowMillis);
        int count = Math.min(Math.max(0, limit), queue.size());
        List<AgentCoordinationEnvelope> drained = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            drained.add(queue.removeFirst());
        }
        return new DrainResult(List.copyOf(drained), expired);
    }

    synchronized int size() {
        return queue.size();
    }

    private int discardExpired(long nowMillis) {
        int expired = 0;
        while (!queue.isEmpty() && queue.peekFirst().expired(nowMillis)) {
            queue.removeFirst();
            expired++;
        }
        return expired;
    }

    record OfferResult(boolean accepted, int expired) {
    }

    record DrainResult(List<AgentCoordinationEnvelope> envelopes, int expired) {
    }
}
