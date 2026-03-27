package server.bots;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

final class BotPerformanceMonitor {
    static final class Config {
        public boolean ENABLED = true;
        public int LOG_INTERVAL_MS = 15000;
    }

    private static final class Stat {
        long count = 0;
        long totalNs = 0;
        long maxNs = 0;
    }

    private static final Logger log = LoggerFactory.getLogger(BotPerformanceMonitor.class);
    private static final Object LOCK = new Object();
    static Config cfg = new Config();

    private static long nextLogAtMs = System.currentTimeMillis() + cfg.LOG_INTERVAL_MS;
    private static final Map<String, Stat> statsBySection = new LinkedHashMap<>();

    private BotPerformanceMonitor() {
    }

    static void record(String section, long elapsedNs) {
        if (!cfg.ENABLED || elapsedNs < 0) {
            return;
        }

        synchronized (LOCK) {
            Stat stat = statsBySection.computeIfAbsent(section, ignored -> new Stat());
            stat.count++;
            stat.totalNs += elapsedNs;
            stat.maxNs = Math.max(stat.maxNs, elapsedNs);
            maybeLog();
        }
    }

    static void recordPathfind(long elapsedNs) {
        record("pathfind", elapsedNs);
    }

    private static void maybeLog() {
        long now = System.currentTimeMillis();
        if (now < nextLogAtMs || statsBySection.isEmpty()) {
            return;
        }

        StringBuilder line = new StringBuilder("bot-perf ");
        boolean first = true;
        for (Map.Entry<String, Stat> entry : statsBySection.entrySet()) {
            if (!first) {
                line.append(" | ");
            }
            first = false;

            Stat stat = entry.getValue();
            double averageMs = stat.totalNs / (double) Math.max(1L, stat.count) / 1_000_000.0;
            double maxMs = stat.maxNs / 1_000_000.0;
            line.append(entry.getKey())
                    .append(" avg=")
                    .append(String.format("%.3f", averageMs))
                    .append("ms")
                    .append(" max=")
                    .append(String.format("%.3f", maxMs))
                    .append("ms")
                    .append(" n=")
                    .append(stat.count);
        }

        log.info(line.toString());
        statsBySection.clear();
        nextLogAtMs = now + cfg.LOG_INTERVAL_MS;
    }
}
