package server.agents.runtime.async;

import server.agents.monitoring.AgentAsyncQueueMetrics;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.mailbox.AgentMailboxOptions;
import server.agents.runtime.mailbox.AgentMailboxSubmission;
import server.agents.runtime.scheduler.AgentSessionId;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Runs blocking Agent work off the scheduler and returns compact results through
 * the generation-bound owning mailbox.
 */
public final class AgentAsyncTaskGateway {
    public enum SubmissionStatus {
        ACCEPTED,
        REJECTED
    }

    public record Submission(long requestId, SubmissionStatus status) {
        public boolean accepted() {
            return status == SubmissionStatus.ACCEPTED;
        }
    }

    @FunctionalInterface
    public interface CompletionHandler<T> {
        void handle(AgentRuntimeEntry entry, AgentAsyncCompletion<T> completion);
    }

    private record PendingKey(AgentSessionId sessionId, AgentAsyncWorkKind kind, String requestKey) {
    }

    private record PendingRequest(AgentRuntimeEntry entry, long requestId) {
    }

    private static final AgentAsyncTaskGateway RUNTIME = new AgentAsyncTaskGateway(
            AgentAsyncExecutorRegistry.runtime(), System::nanoTime);

    private final AgentAsyncExecutorRegistry executors;
    private final LongSupplier nanoTime;
    private final AtomicLong nextRequestId = new AtomicLong();
    private final Map<PendingKey, PendingRequest> pending = new ConcurrentHashMap<>();
    private final Map<PendingKey, Long> latestRequestIds = new ConcurrentHashMap<>();

    public AgentAsyncTaskGateway(AgentAsyncExecutorRegistry executors) {
        this(executors, System::nanoTime);
    }

    AgentAsyncTaskGateway(AgentAsyncExecutorRegistry executors, LongSupplier nanoTime) {
        if (executors == null || nanoTime == null) {
            throw new IllegalArgumentException("Agent async executors and clock are required");
        }
        this.executors = executors;
        this.nanoTime = nanoTime;
    }

    public static AgentAsyncTaskGateway runtime() {
        return RUNTIME;
    }

    public <T> Submission submit(AgentRuntimeEntry entry,
                                 AgentAsyncWorkKind kind,
                                 String requestKey,
                                 Supplier<T> task,
                                 CompletionHandler<T> completionHandler) {
        return submit(entry, kind, requestKey, 0L, task, completionHandler);
    }

    public synchronized <T> Submission submit(AgentRuntimeEntry entry,
                                              AgentAsyncWorkKind kind,
                                              String requestKey,
                                              long timeoutMs,
                                              Supplier<T> task,
                                              CompletionHandler<T> completionHandler) {
        validate(entry, kind, requestKey, task, completionHandler);
        AgentSessionId sessionId = AgentSessionId.from(entry);
        long requestId = nextRequestId.incrementAndGet();
        PendingKey key = new PendingKey(sessionId, kind, requestKey);
        PendingRequest request = new PendingRequest(entry, requestId);
        latestRequestIds.put(key, requestId);
        pending.put(key, request);
        try {
            executors.execute(kind, () -> runTask(key, request, timeoutMs, task, completionHandler));
            return new Submission(requestId, SubmissionStatus.ACCEPTED);
        } catch (RejectedExecutionException rejected) {
            pending.remove(key, request);
            latestRequestIds.remove(key, requestId);
            return new Submission(requestId, SubmissionStatus.REJECTED);
        }
    }

    public synchronized <T> Submission track(AgentRuntimeEntry entry,
                                             AgentAsyncWorkKind kind,
                                             String requestKey,
                                             CompletionStage<T> stage,
                                             CompletionHandler<T> completionHandler) {
        if (entry == null || kind == null || requestKey == null || requestKey.isBlank()
                || stage == null || completionHandler == null) {
            throw new IllegalArgumentException("Agent async completion tracking inputs are required");
        }
        AgentSessionId sessionId = AgentSessionId.from(entry);
        long requestId = nextRequestId.incrementAndGet();
        if (!executors.accepting()) {
            return new Submission(requestId, SubmissionStatus.REJECTED);
        }
        PendingKey key = new PendingKey(sessionId, kind, requestKey);
        PendingRequest request = new PendingRequest(entry, requestId);
        latestRequestIds.put(key, requestId);
        pending.put(key, request);
        long startedAt = nanoTime.getAsLong();
        stage.whenComplete((result, failure) -> complete(
                key,
                request,
                completion(result, failure, sessionId, requestId, kind, requestKey,
                        Math.max(0L, nanoTime.getAsLong() - startedAt)),
                completionHandler));
        return new Submission(requestId, SubmissionStatus.ACCEPTED);
    }

    public void clearSession(int agentCharacterId) {
        pending.keySet().removeIf(key -> key.sessionId().agentCharacterId() == agentCharacterId);
        latestRequestIds.keySet().removeIf(key -> key.sessionId().agentCharacterId() == agentCharacterId);
    }

    public synchronized int clearAll() {
        int cleared = pending.size();
        pending.clear();
        latestRequestIds.clear();
        return cleared;
    }

    public int pendingCount() {
        return pending.size();
    }

    public int pendingCount(AgentSessionId sessionId) {
        if (sessionId == null) {
            return 0;
        }
        int count = 0;
        for (PendingKey key : pending.keySet()) {
            if (key.sessionId().equals(sessionId)) {
                count++;
            }
        }
        return count;
    }

    public boolean isLatest(AgentRuntimeEntry entry,
                            AgentAsyncWorkKind kind,
                            String requestKey,
                            long requestId) {
        if (entry == null || kind == null || requestKey == null) {
            return false;
        }
        PendingKey key = new PendingKey(AgentSessionId.from(entry), kind, requestKey);
        return latestRequestIds.getOrDefault(key, -1L) == requestId;
    }

    private <T> void runTask(PendingKey key,
                             PendingRequest request,
                             long timeoutMs,
                             Supplier<T> task,
                             CompletionHandler<T> completionHandler) {
        long startedAt = nanoTime.getAsLong();
        T result = null;
        Throwable failure = null;
        try {
            result = task.get();
        } catch (Throwable taskFailure) {
            failure = taskFailure;
        }
        long durationNs = Math.max(0L, nanoTime.getAsLong() - startedAt);
        AgentAsyncCompletion<T> completion;
        if (timeoutMs > 0L && durationNs > timeoutMs * 1_000_000L) {
            AgentAsyncQueueMetrics.recordTimedOut(key.kind().metricName(), durationNs);
            completion = new AgentAsyncCompletion<>(key.sessionId(), request.requestId(), key.kind(),
                    key.requestKey(), AgentAsyncCompletion.Status.TIMED_OUT, null, failure, durationNs);
        } else if (failure != null) {
            AgentAsyncQueueMetrics.recordFailed(key.kind().metricName(), durationNs);
            completion = new AgentAsyncCompletion<>(key.sessionId(), request.requestId(), key.kind(),
                    key.requestKey(), AgentAsyncCompletion.Status.FAILED, null, failure, durationNs);
        } else {
            AgentAsyncQueueMetrics.recordCompleted(key.kind().metricName(), durationNs);
            completion = new AgentAsyncCompletion<>(key.sessionId(), request.requestId(), key.kind(),
                    key.requestKey(), AgentAsyncCompletion.Status.SUCCEEDED, result, null, durationNs);
        }
        complete(key, request, completion, completionHandler);
    }

    private <T> void complete(PendingKey key,
                              PendingRequest request,
                              AgentAsyncCompletion<T> completion,
                              CompletionHandler<T> completionHandler) {
        if (!isCurrent(key, request)
                || !AgentRuntimeRegistry.isActiveSession(request.entry(), key.sessionId().generation())) {
            pending.remove(key, request);
            AgentAsyncQueueMetrics.recordStale(key.kind().metricName());
            return;
        }
        String coalescingKey = "async:" + key.kind() + ":" + key.requestKey();
        AgentMailboxSubmission<Void> delivery = AgentMailboxRuntime.submit(
                request.entry(),
                entry -> {
                    if (!isCurrent(key, request)) {
                        AgentAsyncQueueMetrics.recordStale(key.kind().metricName());
                        return null;
                    }
                    try {
                        completionHandler.handle(entry, completion);
                    } finally {
                        pending.remove(key, request);
                    }
                    return null;
                },
                AgentMailboxOptions.completionCoalesceLatest(coalescingKey));
        delivery.result().whenComplete((ignored, failure) -> {
            if (failure != null) {
                pending.remove(key, request);
            }
        });
    }

    private boolean isCurrent(PendingKey key, PendingRequest request) {
        return pending.get(key) == request;
    }

    private static <T> AgentAsyncCompletion<T> completion(T result,
                                                           Throwable failure,
                                                           AgentSessionId sessionId,
                                                           long requestId,
                                                           AgentAsyncWorkKind kind,
                                                           String requestKey,
                                                           long durationNs) {
        if (failure != null) {
            return new AgentAsyncCompletion<>(sessionId, requestId, kind, requestKey,
                    AgentAsyncCompletion.Status.FAILED, null, failure, durationNs);
        }
        return new AgentAsyncCompletion<>(sessionId, requestId, kind, requestKey,
                AgentAsyncCompletion.Status.SUCCEEDED, result, null, durationNs);
    }

    private static <T> void validate(AgentRuntimeEntry entry,
                                     AgentAsyncWorkKind kind,
                                     String requestKey,
                                     Supplier<T> task,
                                     CompletionHandler<T> completionHandler) {
        if (entry == null || kind == null || requestKey == null || requestKey.isBlank()
                || task == null || completionHandler == null) {
            throw new IllegalArgumentException("Agent async task inputs are required");
        }
    }
}
