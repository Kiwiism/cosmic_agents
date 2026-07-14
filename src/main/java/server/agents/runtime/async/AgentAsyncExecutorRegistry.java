package server.agents.runtime.async;

import server.agents.monitoring.AgentAsyncQueueMetrics;
import server.agents.runtime.AgentBoundedExecutorFactory;
import server.agents.runtime.scheduler.AgentLoadSheddingRuntime;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Lazy lifecycle owner for bounded workload-specific Agent executors. */
public final class AgentAsyncExecutorRegistry implements AutoCloseable {
    public record ShutdownResult(int executors,
                                 int queuedTasksCancelled,
                                 Set<AgentAsyncWorkKind> unterminatedExecutors,
                                 boolean interrupted) {
        public ShutdownResult {
            unterminatedExecutors = Set.copyOf(unterminatedExecutors);
        }

        public boolean timedOut() {
            return !unterminatedExecutors.isEmpty();
        }
    }

    private static final AgentAsyncExecutorRegistry RUNTIME = new AgentAsyncExecutorRegistry();

    private final Map<AgentAsyncWorkKind, ThreadPoolExecutor> executors =
            new EnumMap<>(AgentAsyncWorkKind.class);
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public static AgentAsyncExecutorRegistry runtime() {
        return RUNTIME;
    }

    public synchronized void execute(AgentAsyncWorkKind kind, Runnable task) {
        if (kind == null || task == null) {
            throw new IllegalArgumentException("Agent async work kind and task are required");
        }
        if (!accepting.get()) {
            throw new RejectedExecutionException("Agent async executors are stopping");
        }
        if (!AgentLoadSheddingRuntime.permitsAsync(kind)) {
            throw new RejectedExecutionException("Agent async work is paused by load shedding: " + kind);
        }
        executor(kind).execute(task);
    }

    public synchronized boolean isRunning(AgentAsyncWorkKind kind) {
        ThreadPoolExecutor executor = executors.get(kind);
        return executor != null && !executor.isShutdown();
    }

    public synchronized int queueDepth(AgentAsyncWorkKind kind) {
        ThreadPoolExecutor executor = executors.get(kind);
        return executor == null ? 0 : executor.getQueue().size();
    }

    public synchronized int queueCapacity(AgentAsyncWorkKind kind) {
        ThreadPoolExecutor executor = executors.get(kind);
        if (executor == null) {
            return kind.configuredQueueCapacity();
        }
        return executor.getQueue().size() + executor.getQueue().remainingCapacity();
    }

    public synchronized int activeCount(AgentAsyncWorkKind kind) {
        ThreadPoolExecutor executor = executors.get(kind);
        return executor == null ? 0 : executor.getActiveCount();
    }

    public synchronized void start(AgentAsyncWorkKind kind) {
        if (!accepting.get()) {
            throw new RejectedExecutionException("Agent async executors are stopping");
        }
        executor(kind);
    }

    public void startAccepting() {
        accepting.set(true);
    }

    public void stopAccepting() {
        accepting.set(false);
    }

    public boolean accepting() {
        return accepting.get();
    }

    public synchronized void shutdown(AgentAsyncWorkKind kind) {
        ThreadPoolExecutor executor = executors.remove(kind);
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public boolean shutdownAndAwait(AgentAsyncWorkKind kind, long timeout, TimeUnit unit)
            throws InterruptedException {
        ThreadPoolExecutor executor;
        synchronized (this) {
            executor = executors.remove(kind);
        }
        if (executor == null) {
            return true;
        }
        executor.shutdownNow();
        return executor.awaitTermination(timeout, unit);
    }

    public ShutdownResult shutdownAllAndAwait(long timeout, TimeUnit unit) {
        if (timeout < 0L || unit == null) {
            throw new IllegalArgumentException("Agent async shutdown timeout and unit are required");
        }
        stopAccepting();
        Map<AgentAsyncWorkKind, ThreadPoolExecutor> stopping;
        synchronized (this) {
            stopping = new EnumMap<>(executors);
            executors.clear();
        }
        int queuedTasksCancelled = 0;
        for (Map.Entry<AgentAsyncWorkKind, ThreadPoolExecutor> entry : stopping.entrySet()) {
            queuedTasksCancelled += entry.getValue().shutdownNow().size();
            AgentAsyncQueueMetrics.recordDepth(entry.getKey().metricName(), 0);
        }

        long deadline = System.nanoTime() + Math.max(0L, unit.toNanos(timeout));
        Set<AgentAsyncWorkKind> unterminated = EnumSet.noneOf(AgentAsyncWorkKind.class);
        boolean interrupted = false;
        for (Map.Entry<AgentAsyncWorkKind, ThreadPoolExecutor> entry : stopping.entrySet()) {
            long remainingNanos = Math.max(0L, deadline - System.nanoTime());
            try {
                if (!entry.getValue().awaitTermination(remainingNanos, TimeUnit.NANOSECONDS)) {
                    unterminated.add(entry.getKey());
                }
            } catch (InterruptedException failure) {
                interrupted = true;
                unterminated.add(entry.getKey());
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (interrupted) {
            stopping.forEach((kind, executor) -> {
                if (!executor.isTerminated()) {
                    unterminated.add(kind);
                }
            });
        }
        return new ShutdownResult(stopping.size(), queuedTasksCancelled, unterminated, interrupted);
    }

    @Override
    public synchronized void close() {
        accepting.set(false);
        executors.values().forEach(ThreadPoolExecutor::shutdownNow);
        executors.clear();
    }

    private synchronized ThreadPoolExecutor executor(AgentAsyncWorkKind kind) {
        ThreadPoolExecutor existing = executors.get(kind);
        if (existing != null && !existing.isShutdown()) {
            return existing;
        }
        if (existing != null) {
            executors.remove(kind);
        }
        try {
            ThreadPoolExecutor created = AgentBoundedExecutorFactory.fixed(
                    kind.metricName(),
                    kind.threadName(),
                    kind.configuredThreads(),
                    kind.configuredQueueCapacity(),
                    kind.threadPriority());
            executors.put(kind, created);
            return created;
        } catch (RuntimeException failure) {
            throw new RejectedExecutionException("Could not start Agent async executor " + kind, failure);
        }
    }
}
