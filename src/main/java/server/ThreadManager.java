/*
    This file is part of the HeavenMS MapleStory Server
    Copyleft (L) 2016 - 2019 RonanLana

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.
*/
package server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Bounded server work executors. Work that may block must use the blocking or
 * database lane so gameplay work cannot create an unbounded number of threads.
 */
public final class ThreadManager {
    private static final Logger log = LoggerFactory.getLogger(ThreadManager.class);
    private static final ThreadManager instance = new ThreadManager();

    private final Map<Workload, PoolState> pools = new java.util.EnumMap<>(Workload.class);

    public static ThreadManager getInstance() {
        return instance;
    }

    private ThreadManager() {
    }

    public void newTask(Runnable task) {
        submit(Workload.GENERAL, task);
    }

    public void newBlockingTask(Runnable task) {
        submit(Workload.BLOCKING, task);
    }

    public void newDatabaseTask(Runnable task) {
        submit(Workload.DATABASE, task);
    }

    private void submit(Workload workload, Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("task must not be null");
        }
        PoolState pool;
        synchronized (this) {
            pool = pools.get(workload);
        }
        if (pool == null || pool.executor.isShutdown()) {
            log.error("Rejected {} task because ThreadManager is not running", workload.label);
            return;
        }

        pool.submitted.incrementAndGet();
        try {
            pool.executor.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    log.error("Unhandled exception in {} task", workload.label, e);
                }
            });
        } catch (RejectedExecutionException e) {
            long rejected = pool.rejected.incrementAndGet();
            log.warn("Rejected {} task: active={} queued={} completed={} rejected={}", workload.label,
                    pool.executor.getActiveCount(), pool.executor.getQueue().size(),
                    pool.executor.getCompletedTaskCount(), rejected);
        }
    }

    public synchronized void start() {
        if (!pools.isEmpty()) {
            return;
        }
        int processors = Runtime.getRuntime().availableProcessors();
        pools.put(Workload.GENERAL, createPool(Workload.GENERAL,
                configuredInt("general.core", Math.max(4, processors)),
                configuredInt("general.max", Math.max(8, processors * 2)),
                configuredInt("general.queue", 2048)));
        pools.put(Workload.BLOCKING, createPool(Workload.BLOCKING,
                configuredInt("blocking.core", 4),
                configuredInt("blocking.max", 32),
                configuredInt("blocking.queue", 1024)));
        pools.put(Workload.DATABASE, createPool(Workload.DATABASE,
                configuredInt("database.core", 2),
                configuredInt("database.max", 10),
                configuredInt("database.queue", 1024)));
    }

    private PoolState createPool(Workload workload, int configuredCore, int configuredMax, int queueCapacity) {
        int max = Math.max(1, configuredMax);
        int core = Math.min(Math.max(1, configuredCore), max);
        int queue = Math.max(1, queueCapacity);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(core, max, 60, SECONDS,
                new ArrayBlockingQueue<>(queue), namedThreadFactory(workload.label), new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return new PoolState(executor);
    }

    private static ThreadFactory namedThreadFactory(String workload) {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, "cosmic-" + workload + "-" + sequence.incrementAndGet());
            thread.setUncaughtExceptionHandler((failedThread, error) ->
                    log.error("Uncaught error on {}", failedThread.getName(), error));
            return thread;
        };
    }

    static int configuredInt(String suffix, int defaultValue) {
        String propertyName = "cosmic.threads." + suffix;
        String envName = propertyName.toUpperCase(Locale.ROOT).replace('.', '_');
        String raw = System.getProperty(propertyName);
        if (raw == null || raw.isBlank()) {
            raw = System.getenv(envName);
        }
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            log.warn("Ignoring invalid executor setting {}={}", propertyName, raw);
            return defaultValue;
        }
    }

    public void stop() {
        Map<Workload, PoolState> stopping;
        synchronized (this) {
            if (pools.isEmpty()) {
                return;
            }
            stopping = new java.util.EnumMap<>(pools);
            pools.clear();
        }
        stopping.values().forEach(pool -> pool.executor.shutdown());
        boolean interrupted = false;
        for (Map.Entry<Workload, PoolState> entry : stopping.entrySet()) {
            try {
                if (!entry.getValue().executor.awaitTermination(1, MINUTES)) {
                    int cancelled = entry.getValue().executor.shutdownNow().size();
                    log.warn("Forced {} executor shutdown with {} queued tasks cancelled", entry.getKey().label, cancelled);
                }
            } catch (InterruptedException e) {
                interrupted = true;
                entry.getValue().executor.shutdownNow();
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public synchronized String diagnostics() {
        if (pools.isEmpty()) {
            return "ThreadManager stopped";
        }
        StringBuilder result = new StringBuilder("ThreadManager");
        pools.forEach((workload, pool) -> result.append(' ').append(workload.label)
                .append("[active=").append(pool.executor.getActiveCount())
                .append(",queued=").append(pool.executor.getQueue().size())
                .append(",pool=").append(pool.executor.getPoolSize())
                .append(",completed=").append(pool.executor.getCompletedTaskCount())
                .append(",submitted=").append(pool.submitted.get())
                .append(",rejected=").append(pool.rejected.get()).append(']'));
        return result.toString();
    }

    private enum Workload {
        GENERAL("general"),
        BLOCKING("blocking"),
        DATABASE("database");

        private final String label;

        Workload(String label) {
            this.label = label;
        }
    }

    private static final class PoolState {
        private final ThreadPoolExecutor executor;
        private final AtomicLong submitted = new AtomicLong();
        private final AtomicLong rejected = new AtomicLong();

        private PoolState(ThreadPoolExecutor executor) {
            this.executor = executor;
        }
    }
}
