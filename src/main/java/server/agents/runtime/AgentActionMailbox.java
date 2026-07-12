package server.agents.runtime;

import server.agents.monitoring.AgentAsyncQueueMetrics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

/** Bounded per-session FIFO for immutable actions entering from external threads. */
public final class AgentActionMailbox {
    private record Envelope<R>(long sessionGeneration,
                               long transitionGeneration,
                               AgentMailboxAction<R> action,
                               CompletableFuture<R> result) {
    }

    private final ArrayDeque<Envelope<?>> actions = new ArrayDeque<>();
    private final int capacity;
    private boolean closed;

    public AgentActionMailbox(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public synchronized <R> CompletableFuture<R> submit(
            long sessionGeneration,
            AgentMailboxAction<R> action) {
        return submit(sessionGeneration, 0L, action);
    }

    public synchronized <R> CompletableFuture<R> submit(
            long sessionGeneration,
            long transitionGeneration,
            AgentMailboxAction<R> action) {
        CompletableFuture<R> result = new CompletableFuture<>();
        if (closed) {
            result.completeExceptionally(new RejectedExecutionException("Agent mailbox is closed"));
            AgentAsyncQueueMetrics.recordRejected("mailbox", actions.size());
            return result;
        }
        if (actions.size() >= capacity) {
            result.completeExceptionally(new RejectedExecutionException("Agent mailbox is full"));
            AgentAsyncQueueMetrics.recordRejected("mailbox", actions.size());
            return result;
        }
        actions.addLast(new Envelope<>(sessionGeneration, transitionGeneration, action, result));
        AgentAsyncQueueMetrics.recordSubmitted("mailbox", actions.size());
        return result;
    }

    public int drain(AgentRuntimeEntry entry, int maxActions) {
        int drained = 0;
        int limit = Math.max(1, maxActions);
        while (drained < limit) {
            Envelope<?> envelope;
            synchronized (this) {
                envelope = actions.pollFirst();
                AgentAsyncQueueMetrics.recordDepth("mailbox", actions.size());
            }
            if (envelope == null) {
                break;
            }
            execute(entry, envelope);
            drained++;
        }
        return drained;
    }

    public void close() {
        List<Envelope<?>> pending;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            pending = new ArrayList<>(actions);
            actions.clear();
            AgentAsyncQueueMetrics.recordDepth("mailbox", 0);
        }
        RejectedExecutionException failure = new RejectedExecutionException("Agent session was removed");
        pending.forEach(envelope -> envelope.result().completeExceptionally(failure));
    }

    public synchronized int size() {
        return actions.size();
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    private static <R> void execute(AgentRuntimeEntry entry, Envelope<R> envelope) {
        if (envelope.result().isCancelled()) {
            return;
        }
        if (entry == null
                || envelope.sessionGeneration() != entry.sessionGeneration()
                || !entry.transitionBarrierState().isCurrentGeneration(envelope.transitionGeneration())) {
            envelope.result().completeExceptionally(
                    new RejectedExecutionException("Agent action belongs to a stale session or profile generation"));
            return;
        }
        try {
            envelope.result().complete(envelope.action().execute(entry));
        } catch (Throwable failure) {
            envelope.result().completeExceptionally(failure);
        }
    }
}
