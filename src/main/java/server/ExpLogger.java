package server;

import config.YamlConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/** Asynchronous, bounded persistence for optional EXP gain audit records. */
public final class ExpLogger {
    private static final Logger log = LoggerFactory.getLogger(ExpLogger.class);
    private static final int QUEUE_CAPACITY = configuredInt("queue", 100_000);
    private static final int BATCH_SIZE = configuredInt("batch", 1_000);
    private static final int FLUSH_INTERVAL_SECONDS = configuredInt("interval.seconds", 60);
    private static final BoundedBatchBuffer<ExpLogRecord> queue = new BoundedBatchBuffer<>(QUEUE_CAPACITY);
    private static final AtomicLong accepted = new AtomicLong();
    private static final AtomicLong persisted = new AtomicLong();
    private static final AtomicLong dropped = new AtomicLong();
    private static final AtomicLong failures = new AtomicLong();
    private static final AtomicLong lastDropWarningMillis = new AtomicLong();
    private static final AtomicBoolean stopped = new AtomicBoolean();
    private static final Object flushLock = new Object();
    private static ScheduledExecutorService scheduler;

    private ExpLogger() {
    }

    public record ExpLogRecord(int worldExpRate, int expCoupon, long gainedExp, int currentExp,
                               Timestamp expGainTime, int charid) {
    }

    public static void putExpLogRecord(ExpLogRecord record) {
        if (record == null || stopped.get()) {
            return;
        }
        if (queue.offer(record)) {
            accepted.incrementAndGet();
            return;
        }
        long totalDropped = dropped.incrementAndGet();
        long now = System.currentTimeMillis();
        long previous = lastDropWarningMillis.get();
        if (now - previous >= MINUTES.toMillis(1) && lastDropWarningMillis.compareAndSet(previous, now)) {
            log.warn("EXP log queue full; dropped={} queued={} capacity={}", totalDropped, queue.size(), QUEUE_CAPACITY);
        }
    }

    private static void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "cosmic-exp-logger");
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(ExpLogger::flushOneBatchSafely,
                FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(ExpLogger::shutdown, "cosmic-exp-logger-shutdown"));
    }

    private static void flushOneBatchSafely() {
        try {
            flushOneBatch();
        } catch (RuntimeException e) {
            failures.incrementAndGet();
            log.error("Unexpected EXP log flush failure", e);
        }
    }

    static boolean flushOneBatch() {
        synchronized (flushLock) {
            List<ExpLogRecord> batch = queue.drain(BATCH_SIZE);
            if (batch.isEmpty()) {
                return true;
            }

            try (Connection con = DatabaseConnection.getConnection()) {
                boolean originalAutoCommit = con.getAutoCommit();
                con.setAutoCommit(false);
                boolean committed = false;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO characterexplogs (world_exp_rate, exp_coupon, gained_exp, current_exp, exp_gain_time, charid) VALUES (?, ?, ?, ?, ?, ?)")) {
                    for (ExpLogRecord record : batch) {
                        ps.setInt(1, record.worldExpRate());
                        ps.setInt(2, record.expCoupon());
                        ps.setLong(3, record.gainedExp());
                        ps.setInt(4, record.currentExp());
                        ps.setTimestamp(5, record.expGainTime());
                        ps.setInt(6, record.charid());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                    con.commit();
                    committed = true;
                    persisted.addAndGet(batch.size());
                    return true;
                } catch (SQLException e) {
                    if (!committed) {
                        try {
                            con.rollback();
                        } catch (SQLException rollbackError) {
                            e.addSuppressed(rollbackError);
                        }
                    }
                    throw e;
                } finally {
                    try {
                        con.setAutoCommit(originalAutoCommit);
                    } catch (SQLException restoreError) {
                        if (committed) {
                            log.warn("EXP log batch committed but connection auto-commit restoration failed", restoreError);
                        } else {
                            log.warn("EXP log connection auto-commit restoration failed", restoreError);
                        }
                    }
                }
            } catch (SQLException e) {
                failures.incrementAndGet();
                long requeueDrops = queue.requeue(batch);
                if (requeueDrops > 0) {
                    dropped.addAndGet(requeueDrops);
                }
                log.error("Failed to persist EXP log batch; batch={} requeued={} lost={} queued={}",
                        batch.size(), batch.size() - requeueDrops, requeueDrops, queue.size(), e);
                return false;
            }
        }
    }

    public static void shutdown() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(30, SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        while (!queue.isEmpty() && flushOneBatch()) {
            // Synchronous checkpoint: drain all available batches before DB shutdown.
        }
        if (!queue.isEmpty()) {
            log.error("EXP logger stopped with {} unpersisted records", queue.size());
        }
    }

    public static String diagnostics() {
        return "ExpLogger queued=" + queue.size() + "/" + QUEUE_CAPACITY
                + " accepted=" + accepted.get()
                + " persisted=" + persisted.get()
                + " dropped=" + dropped.get()
                + " failures=" + failures.get();
    }

    private static int configuredInt(String suffix, int defaultValue) {
        String propertyName = "cosmic.expLogger." + suffix;
        String envName = propertyName.toUpperCase(Locale.ROOT).replace('.', '_');
        String raw = System.getProperty(propertyName);
        if (raw == null || raw.isBlank()) {
            raw = System.getenv(envName);
        }
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            log.warn("Ignoring invalid EXP logger setting {}={}", propertyName, raw);
            return defaultValue;
        }
    }

    static {
        if (YamlConfig.config.server.USE_EXP_GAIN_LOG) {
            start();
        } else {
            stopped.set(true);
        }
    }
}
