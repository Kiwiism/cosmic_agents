package server.agents.runtime;

import server.agents.monitoring.AgentAsyncQueueMetrics;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Creates daemon Agent workers with an explicit FIFO queue bound and rejection signal. */
public final class AgentBoundedExecutorFactory {
    private AgentBoundedExecutorFactory() {
    }

    public static ThreadPoolExecutor fixed(String threadName, int threads, int queueCapacity) {
        return fixed(threadName, threadName, threads, queueCapacity);
    }

    public static ThreadPoolExecutor fixed(String metricName, String threadName, int threads, int queueCapacity) {
        return fixed(metricName, threadName, threads, queueCapacity, Thread.NORM_PRIORITY);
    }

    public static ThreadPoolExecutor fixed(String metricName,
                                           String threadName,
                                           int threads,
                                           int queueCapacity,
                                           int threadPriority) {
        int workerCount = Math.max(1, threads);
        int capacity = Math.max(1, queueCapacity);
        int priority = Math.clamp(threadPriority, Thread.MIN_PRIORITY, Thread.MAX_PRIORITY);
        AgentAsyncQueueMetrics.recordCapacity(metricName, capacity);
        return new ThreadPoolExecutor(
                workerCount,
                workerCount,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity),
                runnable -> {
                    Thread thread = new Thread(runnable, threadName);
                    thread.setDaemon(true);
                    thread.setPriority(priority);
                    return thread;
                },
                (task, executor) -> {
                    AgentAsyncQueueMetrics.recordRejected(metricName, executor.getQueue().size());
                    throw new java.util.concurrent.RejectedExecutionException(
                            "Agent queue is full: " + metricName);
                }) {
            @Override
            public void execute(Runnable command) {
                super.execute(command);
                AgentAsyncQueueMetrics.recordSubmitted(metricName, getQueue().size());
            }

            @Override
            protected void beforeExecute(Thread thread, Runnable task) {
                AgentAsyncQueueMetrics.recordWorkerStarted(metricName, getQueue().size());
                super.beforeExecute(thread, task);
            }

            @Override
            protected void afterExecute(Runnable task, Throwable failure) {
                super.afterExecute(task, failure);
                AgentAsyncQueueMetrics.recordWorkerStopped(metricName, getQueue().size());
            }
        };
    }

    public static int positiveIntegerProperty(String name, int defaultValue) {
        return Math.max(1, Integer.getInteger(name, defaultValue));
    }
}
