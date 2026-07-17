package server.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DiskSpaceMonitor {
    private static final Logger log = LoggerFactory.getLogger(DiskSpaceMonitor.class);
    private static final long DEFAULT_WARN_FREE_BYTES = 10L * 1024 * 1024 * 1024;
    private static final long DEFAULT_CRITICAL_FREE_BYTES = 2L * 1024 * 1024 * 1024;
    private static final Path MONITORED_PATH = Path.of(
            setting("cosmic.disk.path", "COSMIC_DISK_PATH", "."));
    private static final long WARN_FREE_BYTES = longSetting(
            "cosmic.disk.warnFreeBytes", "COSMIC_DISK_WARN_FREE_BYTES", DEFAULT_WARN_FREE_BYTES);
    private static final long CRITICAL_FREE_BYTES = longSetting(
            "cosmic.disk.criticalFreeBytes", "COSMIC_DISK_CRITICAL_FREE_BYTES", DEFAULT_CRITICAL_FREE_BYTES);
    private static volatile Snapshot latest = Snapshot.unknown(MONITORED_PATH.toAbsolutePath());

    private DiskSpaceMonitor() {
    }

    public static Snapshot checkNow() {
        Snapshot previous = latest;
        Snapshot current;
        try {
            Path path = MONITORED_PATH.toAbsolutePath();
            Files.createDirectories(path);
            FileStore store = Files.getFileStore(path);
            long usableBytes = store.getUsableSpace();
            long totalBytes = store.getTotalSpace();
            current = new Snapshot(path, usableBytes, totalBytes,
                    classify(usableBytes, WARN_FREE_BYTES, CRITICAL_FREE_BYTES));
        } catch (IOException | RuntimeException failure) {
            current = Snapshot.unknown(MONITORED_PATH.toAbsolutePath());
            if (previous.level() != Level.UNKNOWN) {
                log.warn("Unable to inspect disk space for {}", MONITORED_PATH.toAbsolutePath(), failure);
            }
        }
        latest = current;
        logTransition(previous, current);
        return current;
    }

    public static Snapshot latest() {
        return latest;
    }

    public static boolean hasRoomFor(Path target, long expectedBytes) {
        try {
            Path absolute = target.toAbsolutePath();
            Path directory = Files.isDirectory(absolute) ? absolute : absolute.getParent();
            if (directory == null) {
                directory = Path.of(".").toAbsolutePath();
            }
            Files.createDirectories(directory);
            long usableBytes = Files.getFileStore(directory).getUsableSpace();
            return usableBytes - Math.max(0, expectedBytes) >= CRITICAL_FREE_BYTES;
        } catch (IOException | RuntimeException failure) {
            log.warn("Unable to verify free space for {}", target.toAbsolutePath(), failure);
            return false;
        }
    }

    static Level classify(long usableBytes, long warnFreeBytes, long criticalFreeBytes) {
        if (usableBytes <= criticalFreeBytes) {
            return Level.CRITICAL;
        }
        if (usableBytes <= warnFreeBytes) {
            return Level.WARN;
        }
        return Level.OK;
    }

    private static void logTransition(Snapshot previous, Snapshot current) {
        if (previous.level() == current.level()) {
            return;
        }
        switch (current.level()) {
            case CRITICAL -> log.error("Disk space critical: {}", current.compact());
            case WARN -> log.warn("Disk space low: {}", current.compact());
            case OK -> log.info("Disk space healthy: {}", current.compact());
            case UNKNOWN -> {
            }
        }
    }

    private static long longSetting(String propertyName, String environmentName, long defaultValue) {
        String raw = setting(propertyName, environmentName, null);
        if (raw == null) {
            return defaultValue;
        }
        try {
            long parsed = Long.parseLong(raw.trim());
            return parsed >= 0 ? parsed : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String setting(String propertyName, String environmentName, String defaultValue) {
        String property = System.getProperty(propertyName);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String environment = System.getenv(environmentName);
        return environment == null || environment.isBlank() ? defaultValue : environment;
    }

    public enum Level {
        OK,
        WARN,
        CRITICAL,
        UNKNOWN
    }

    public record Snapshot(Path path, long usableBytes, long totalBytes, Level level) {
        private static Snapshot unknown(Path path) {
            return new Snapshot(path, -1, -1, Level.UNKNOWN);
        }

        public String compact() {
            if (level == Level.UNKNOWN) {
                return "disk=unknown";
            }
            return "disk=" + level.name().toLowerCase()
                    + ":" + usableBytes / 1024 / 1024 + "/" + totalBytes / 1024 / 1024 + "MB";
        }
    }
}
