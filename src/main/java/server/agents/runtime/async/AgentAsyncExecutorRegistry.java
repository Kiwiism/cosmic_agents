package server.agents.runtime.async;

import server.agents.runtime.AgentBoundedExecutorFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Lazy lifecycle owner for bounded workload-specific Agent executors. */
public final class AgentAsyncExecutorRegistry implements AutoCloseable {
    private static final AgentAsyncExecutorRegistry RUNTIME = new AgentAsyncExecutorRegistry();

    private final Map<AgentAsyncWorkKind, ThreadPoolExecutor> executors =
            new EnumMap<>(AgentAsyncWorkKind.class);

    public static AgentAsyncExecutorRegistry runtime() {
        return RUNTIME;
    }

    public void execute(AgentAsyncWorkKind kind, Runnable task) {
        if (kind == null || task == null) {
            throw new IllegalArgumentException("Agent async work kind and task are required");
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
        executor(kind);
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

    @Override
    public synchronized void close() {
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
