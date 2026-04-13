package server.bots;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

final class BotPerformanceMonitor {
    static final class Config {
        public boolean ENABLED = true;
        public int LOG_INTERVAL_MS = 15000;
        public double SLOW_SAMPLE_MS = 5.0;
    }

    private static final class Stat {
        long count = 0;
        long totalNs = 0;
        long maxNs = 0;
        long slowCount = 0;
        long slowTotalNs = 0;
    }

    private static final Logger log = LoggerFactory.getLogger(BotPerformanceMonitor.class);
    private static final Object LOCK = new Object();
    static Config cfg = new Config();

    private static long lastLogAtMs = System.currentTimeMillis();
    private static long nextLogAtMs = lastLogAtMs + cfg.LOG_INTERVAL_MS;
    private static final Map<String, Stat> statsBySection = new LinkedHashMap<>();
    private static final Map<String, String> SECTION_NOTES = Map.of(
            "move-ground", "ground physics, foothold collision, fallback steering, and movement packet sync",
            "move-air", "air physics, foothold landing checks, and air steering",
            "move-climb", "rope/ladder attachment, climb movement, and dismount checks",
            "nav-resolve", "region lookup, graph/path selection, edge reuse, and waypoint selection",
            "pathfind", "A* search over the current bot navigation graph",
            "pathfind-target-score", "A* called while ranking grind target regions",
            "combat-target-search", "monster scan, distance filtering, foothold lookup, and candidate sorting",
            "combat-plan", "skill/basic attack route selection and hitbox construction"
    );

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
            if (elapsedNs >= slowThresholdNs()) {
                stat.slowCount++;
                stat.slowTotalNs += elapsedNs;
            }
            maybeLog();
        }
    }

    static void recordPathfind(long elapsedNs) {
        record("pathfind", elapsedNs);
    }

    static void recordPathfind(String caller, long elapsedNs) {
        if (caller == null || caller.isBlank()) {
            recordPathfind(elapsedNs);
            return;
        }
        record("pathfind-" + caller, elapsedNs);
    }

    private static void maybeLog() {
        long now = System.currentTimeMillis();
        if (now < nextLogAtMs || statsBySection.isEmpty()) {
            return;
        }

        double intervalSeconds = Math.max(0.001, (now - lastLogAtMs) / 1000.0);
        StringBuilder line = new StringBuilder("bot-perf slow>=")
                .append(String.format("%.3f", cfg.SLOW_SAMPLE_MS))
                .append("ms ");
        boolean first = true;
        for (Map.Entry<String, Stat> entry : statsBySection.entrySet()) {
            Stat stat = entry.getValue();
            if (stat.maxNs < slowThresholdNs()) {
                continue;
            }
            if (!first) {
                line.append(" | ");
            }
            first = false;

            double averageMs = stat.totalNs / (double) Math.max(1L, stat.count) / 1_000_000.0;
            double totalMs = stat.totalNs / 1_000_000.0;
            double maxMs = stat.maxNs / 1_000_000.0;
            double slowAverageMs = stat.slowTotalNs / (double) Math.max(1L, stat.slowCount) / 1_000_000.0;
            line.append(entry.getKey())
                    .append(" avg=")
                    .append(String.format("%.3f", averageMs))
                    .append("ms")
                    .append(" total=")
                    .append(String.format("%.3f", totalMs))
                    .append("ms")
                    .append(" cps=")
                    .append(String.format("%.1f", stat.count / intervalSeconds))
                    .append(" cpuMsPerSec=")
                    .append(String.format("%.3f", totalMs / intervalSeconds))
                    .append(" max=")
                    .append(String.format("%.3f", maxMs))
                    .append("ms")
                    .append(" n=")
                    .append(stat.count)
                    .append(" slow=")
                    .append(stat.slowCount)
                    .append("/")
                    .append(stat.count)
                    .append(" slowAvg=")
                    .append(String.format("%.3f", slowAverageMs))
                    .append("ms")
                    .append(" note=")
                    .append(noteFor(entry.getKey()));
        }

        if (!first) {
            log.info(line.toString());
        }
        statsBySection.clear();
        lastLogAtMs = now;
        nextLogAtMs = now + cfg.LOG_INTERVAL_MS;
    }

    private static String noteFor(String section) {
        String note = SECTION_NOTES.get(section);
        if (note != null) {
            return note;
        }
        if (section != null && section.startsWith("pathfind-")) {
            return "A* search over the current bot navigation graph";
        }
        return "instrumented bot subsystem";
    }

    private static long slowThresholdNs() {
        return (long) (Math.max(0.0, cfg.SLOW_SAMPLE_MS) * 1_000_000.0);
    }
}
