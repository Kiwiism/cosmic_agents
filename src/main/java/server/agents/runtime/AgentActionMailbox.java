package server.agents.runtime;

import server.agents.monitoring.AgentAsyncQueueMetrics;
import server.agents.runtime.mailbox.AgentMailboxFailureReason;
import server.agents.runtime.mailbox.AgentMailboxOptions;
import server.agents.runtime.mailbox.AgentMailboxOverflowPolicy;
import server.agents.runtime.mailbox.AgentMailboxRejectedException;
import server.agents.runtime.mailbox.AgentMailboxSubmission;
import server.agents.runtime.mailbox.AgentMailboxSubmissionStatus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

/** Bounded per-session FIFO for immutable actions entering from external threads. */
public final class AgentActionMailbox {
    private record Envelope<R>(long sessionGeneration,
                               AgentMailboxAction<R> action,
                               AgentMailboxOptions options,
                               CompletableFuture<R> result) {
    }

    private final ArrayDeque<Envelope<?>> actions = new ArrayDeque<>();
    private final int capacity;
    private final LongSupplier nowMs;
    private boolean closed;

    public AgentActionMailbox(int capacity) {
        this(capacity, System::currentTimeMillis);
    }

    AgentActionMailbox(int capacity, LongSupplier nowMs) {
        this.capacity = Math.max(1, capacity);
        this.nowMs = nowMs;
    }

    public <R> CompletableFuture<R> submit(
            long sessionGeneration,
            AgentMailboxAction<R> action) {
        return submit(sessionGeneration, action, AgentMailboxOptions.fifo()).result();
    }

    public <R> AgentMailboxSubmission<R> submit(
            long sessionGeneration,
            AgentMailboxAction<R> action,
            AgentMailboxOptions options) {
        if (action == null || options == null) {
            throw new IllegalArgumentException("Agent mailbox action and options are required");
        }
        CompletableFuture<R> result = new CompletableFuture<>();
        Envelope<?> replaced = null;
        AgentMailboxSubmissionStatus status;
        int depth;
        synchronized (this) {
            if (closed) {
                return rejected(result, AgentMailboxSubmissionStatus.REJECTED_CLOSED,
                        AgentMailboxFailureReason.CLOSED, "Agent mailbox is closed", actions.size());
            }
            if (options.expired(nowMs.getAsLong())) {
                return rejected(result, AgentMailboxSubmissionStatus.REJECTED_EXPIRED,
                        AgentMailboxFailureReason.EXPIRED, "Agent mailbox action already expired", actions.size());
            }
            if (options.overflowPolicy() == AgentMailboxOverflowPolicy.COALESCE_LATEST) {
                replaced = findByCoalescingKey(options.coalescingKey());
                if (replaced != null) {
                    actions.remove(replaced);
                }
            }
            if (actions.size() >= capacity) {
                return rejected(result, AgentMailboxSubmissionStatus.REJECTED_FULL,
                        AgentMailboxFailureReason.FULL, "Agent mailbox is full", actions.size());
            }
            actions.addLast(new Envelope<>(sessionGeneration, action, options, result));
            depth = actions.size();
            status = replaced == null
                    ? AgentMailboxSubmissionStatus.ACCEPTED
                    : AgentMailboxSubmissionStatus.COALESCED;
        }
        if (replaced != null) {
            reject(replaced, AgentMailboxFailureReason.COALESCED,
                    "Agent mailbox action was replaced by a newer action");
            AgentAsyncQueueMetrics.recordCoalesced("mailbox", depth);
        }
        AgentAsyncQueueMetrics.recordSubmitted("mailbox", depth);
        return new AgentMailboxSubmission<>(status, result);
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
        reject(pending, AgentMailboxFailureReason.CLOSED, "Agent session was removed");
    }

    public void discardPending(String reason) {
        List<Envelope<?>> pending;
        synchronized (this) {
            pending = new ArrayList<>(actions);
            actions.clear();
            AgentAsyncQueueMetrics.recordDepth("mailbox", 0);
        }
        reject(pending, AgentMailboxFailureReason.DISCARDED, reason);
    }

    public synchronized int size() {
        return actions.size();
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    private Envelope<?> findByCoalescingKey(String key) {
        for (Envelope<?> envelope : actions) {
            if (key.equals(envelope.options().coalescingKey())) {
                return envelope;
            }
        }
        return null;
    }

    private static <R> AgentMailboxSubmission<R> rejected(
            CompletableFuture<R> result,
            AgentMailboxSubmissionStatus status,
            AgentMailboxFailureReason reason,
            String message,
            int depth) {
        result.completeExceptionally(new AgentMailboxRejectedException(reason, message));
        AgentAsyncQueueMetrics.recordRejected("mailbox", depth);
        return new AgentMailboxSubmission<>(status, result);
    }

    private static void reject(List<Envelope<?>> pending,
                               AgentMailboxFailureReason reason,
                               String message) {
        pending.forEach(envelope -> reject(envelope, reason, message));
    }

    private static void reject(Envelope<?> envelope,
                               AgentMailboxFailureReason reason,
                               String message) {
        envelope.result().completeExceptionally(new AgentMailboxRejectedException(reason, message));
    }

    private <R> void execute(AgentRuntimeEntry entry, Envelope<R> envelope) {
        if (envelope.result().isCancelled()) {
            return;
        }
        if (entry == null || envelope.sessionGeneration() != entry.sessionGeneration()) {
            reject(envelope, AgentMailboxFailureReason.STALE_SESSION,
                    "Agent action belongs to a stale session");
            return;
        }
        if (envelope.options().expired(nowMs.getAsLong())) {
            reject(envelope, AgentMailboxFailureReason.EXPIRED, "Agent mailbox action expired");
            return;
        }
        try {
            envelope.result().complete(envelope.action().execute(entry));
        } catch (Throwable failure) {
            envelope.result().completeExceptionally(failure);
        }
    }
}
