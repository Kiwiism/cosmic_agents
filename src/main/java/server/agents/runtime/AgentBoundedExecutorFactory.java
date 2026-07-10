package server.agents.runtime;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Creates daemon Agent workers with an explicit FIFO queue bound and rejection signal. */
public final class AgentBoundedExecutorFactory {
    private AgentBoundedExecutorFactory() {
    }

    public static ThreadPoolExecutor fixed(String threadName, int threads, int queueCapacity) {
        int workerCount = Math.max(1, threads);
        int capacity = Math.max(1, queueCapacity);
        return new ThreadPoolExecutor(
                workerCount,
                workerCount,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity),
                runnable -> {
                    Thread thread = new Thread(runnable, threadName);
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    public static int positiveIntegerProperty(String name, int defaultValue) {
        return Math.max(1, Integer.getInteger(name, defaultValue));
    }
}
