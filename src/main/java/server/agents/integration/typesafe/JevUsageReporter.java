package server.agents.integration.typesafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.TimerManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Periodically writes the live usage report to the server log ({@code [typesafe-usage]}) and
 * appends per-kind rows to {@code logs/typesafe-usage.csv}, so spend is visible while the
 * server runs and can be graphed afterwards. Started only when a key is configured.
 */
public final class JevUsageReporter {
    private static final Logger log = LoggerFactory.getLogger(JevUsageReporter.class);
    private static final long LOG_INTERVAL_MINUTES = config.AgentTuning.longValue(
            "server.agents.integration.typesafe.JevUsageReporter.LOG_INTERVAL_MINUTES");
    public static final Path CSV_PATH = Path.of("logs", "typesafe-usage.csv");
    private static volatile ScheduledFuture<?> schedule;

    private JevUsageReporter() {
    }

    public static void start() {
        JevClient client = JevClient.runtime();
        if (!client.configured() || LOG_INTERVAL_MINUTES <= 0 || schedule != null) {
            return;
        }
        long intervalMs = TimeUnit.MINUTES.toMillis(LOG_INTERVAL_MINUTES);
        schedule = TimerManager.getInstance().register(
                TimerManager.SchedulerLane.LOW_PRIORITY, JevUsageReporter::report, intervalMs, intervalMs);
        log.info("TypeSafe usage reporting every {} min to the log and {}", LOG_INTERVAL_MINUTES, CSV_PATH);
    }

    public static void stop() {
        ScheduledFuture<?> current = schedule;
        if (current != null) {
            current.cancel(false);
            schedule = null;
        }
    }

    /** One report cycle: log lines plus CSV rows. Safe to call from a command as well. */
    public static void report() {
        JevUsageMeter meter = JevClient.runtime().meter();
        long now = System.currentTimeMillis();
        if (meter.requests() == 0) {
            log.info("[typesafe-usage] no requests yet");
            return;
        }
        for (String line : meter.report(now)) {
            log.info("[typesafe-usage] {}", line);
        }
        appendCsv(meter.csvRows(now));
    }

    static void appendCsv(List<String> rows) {
        try {
            Path parent = CSV_PATH.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            boolean fresh = !Files.exists(CSV_PATH) || Files.size(CSV_PATH) == 0;
            StringBuilder text = new StringBuilder();
            if (fresh) {
                text.append(JevUsageMeter.csvHeader()).append('\n');
            }
            for (String row : rows) {
                text.append(row).append('\n');
            }
            Files.writeString(CSV_PATH, text.toString(), StandardCharsets.US_ASCII,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            log.warn("could not append TypeSafe usage CSV {}", CSV_PATH, failure);
        }
    }
}
