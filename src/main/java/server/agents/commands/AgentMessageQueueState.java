package server.agents.commands;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Mutable Agent reply queue state while runtime session storage is still being split.
 */
public final class AgentMessageQueueState {
    private final ArrayDeque<AgentQueuedMessage> queue = new ArrayDeque<>();
    private boolean sending;

    public Object lock() {
        return queue;
    }

    public Deque<AgentQueuedMessage> queue() {
        return queue;
    }

    public int size() {
        return queue.size();
    }

    public void enqueue(AgentQueuedMessage message) {
        queue.add(message);
    }

    public AgentQueuedMessage poll() {
        return queue.poll();
    }

    public AgentQueuedMessage peek() {
        return queue.peek();
    }

    public List<AgentQueuedMessage> snapshot() {
        synchronized (lock()) {
            return List.copyOf(new ArrayList<>(queue));
        }
    }

    public boolean isSending() {
        return sending;
    }

    public void setSending(boolean sending) {
        this.sending = sending;
    }

    public boolean isIdle() {
        synchronized (lock()) {
            return !sending && queue.isEmpty();
        }
    }
}
