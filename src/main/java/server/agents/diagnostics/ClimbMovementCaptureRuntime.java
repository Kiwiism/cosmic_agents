package server.agents.diagnostics;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Point;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived, opt-in capture of native and synthetic player movement fragments.
 *
 * <p>The hot-path hooks only copy packet data while a GM-created session exists for
 * the movement source. Captures are bounded and are written as text when stopped.</p>
 */
public final class ClimbMovementCaptureRuntime {
    public static final int DEFAULT_PACKET_LIMIT = 120;
    public static final int MAX_PACKET_LIMIT = 500;

    private static final Logger log = LoggerFactory.getLogger(ClimbMovementCaptureRuntime.class);
    private static final Object sessionLock = new Object();
    private static final Map<Integer, CaptureSession> sessionsByTargetId = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> targetIdsByOwnerId = new ConcurrentHashMap<>();
    private static final DateTimeFormatter filenameTime = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneId.systemDefault());

    private ClimbMovementCaptureRuntime() {
    }

    public enum Source {
        NATIVE_CLIENT,
        SYNTHETIC_AGENT
    }

    public record OperationResult(boolean success, String message) {
    }

    public record Status(boolean active,
                         String targetName,
                         int packetCount,
                         int packetLimit,
                         boolean limitReached,
                         long elapsedMillis) {
        public static Status inactive() {
            return new Status(false, "", 0, 0, false, 0L);
        }
    }

    public record StopResult(boolean success, String message, Path reportPath, int packetCount) {
    }

    public static OperationResult start(Character owner, Character target, int packetLimit) {
        if (owner == null || target == null) {
            return new OperationResult(false, "Capture owner and target must both be online.");
        }
        if (packetLimit < 1 || packetLimit > MAX_PACKET_LIMIT) {
            return new OperationResult(false,
                    "Packet limit must be between 1 and " + MAX_PACKET_LIMIT + ".");
        }

        synchronized (sessionLock) {
            if (targetIdsByOwnerId.containsKey(owner.getId())) {
                return new OperationResult(false,
                        "You already have an active movement capture; stop or clear it first.");
            }
            CaptureSession existing = sessionsByTargetId.get(target.getId());
            if (existing != null) {
                return new OperationResult(false,
                        target.getName() + " is already being captured by " + existing.ownerName + ".");
            }

            CaptureSession session = new CaptureSession(owner, target, packetLimit);
            sessionsByTargetId.put(target.getId(), session);
            targetIdsByOwnerId.put(owner.getId(), target.getId());
        }

        return new OperationResult(true,
                "Movement capture started for " + target.getName() + " (limit " + packetLimit + ").");
    }

    /** Fast-path probe used to avoid copying native packet bytes unless a capture is active. */
    public static boolean isCapturing(int targetCharacterId) {
        return sessionsByTargetId.containsKey(targetCharacterId);
    }

    public static void recordNative(Character target, byte[] movementData) {
        record(target, Source.NATIVE_CLIENT, movementData);
    }

    public static void recordSynthetic(Character target, byte[] movementData) {
        record(target, Source.SYNTHETIC_AGENT, movementData);
    }

    public static Status status(Character owner) {
        if (owner == null) {
            return Status.inactive();
        }
        CaptureSession session = sessionOwnedBy(owner.getId());
        if (session == null) {
            return Status.inactive();
        }
        synchronized (session) {
            return new Status(
                    session.active,
                    session.targetName,
                    session.records.size(),
                    session.packetLimit,
                    session.records.size() >= session.packetLimit,
                    elapsedMillis(session.startedNanos));
        }
    }

    public static StopResult stop(Character owner) {
        CaptureSnapshot snapshot = removeOwnedSession(owner);
        if (snapshot == null) {
            return new StopResult(false, "You do not have an active movement capture.", null, 0);
        }

        try {
            Path report = writeReport(snapshot);
            log.info("Movement capture for {} wrote {} packet(s) to {}",
                    snapshot.targetName, snapshot.records.size(), report.toAbsolutePath());
            return new StopResult(true,
                    "Captured " + snapshot.records.size() + " movement packet(s).",
                    report.toAbsolutePath(),
                    snapshot.records.size());
        } catch (IOException failure) {
            log.error("Unable to write movement capture for {}", snapshot.targetName, failure);
            return new StopResult(false,
                    "Captured " + snapshot.records.size() + " packet(s), but report writing failed: "
                            + failure.getMessage(),
                    null,
                    snapshot.records.size());
        }
    }

    public static OperationResult clear(Character owner) {
        CaptureSnapshot removed = removeOwnedSession(owner);
        if (removed == null) {
            return new OperationResult(false, "You do not have an active movement capture.");
        }
        return new OperationResult(true,
                "Discarded " + removed.records.size() + " captured packet(s) for " + removed.targetName + ".");
    }

    private static void record(Character target, Source source, byte[] movementData) {
        if (target == null || movementData == null) {
            return;
        }
        CaptureSession session = sessionsByTargetId.get(target.getId());
        if (session == null) {
            return;
        }

        synchronized (session) {
            if (!session.active || session.records.size() >= session.packetLimit) {
                return;
            }
            Point position = target.getPosition();
            Point copiedPosition = position == null ? null : new Point(position);
            session.records.add(new CaptureRecord(
                    session.records.size() + 1,
                    elapsedMillis(session.startedNanos),
                    source,
                    target.getMapId(),
                    copiedPosition,
                    target.getStance(),
                    Arrays.copyOf(movementData, movementData.length)));
        }
    }

    private static CaptureSession sessionOwnedBy(int ownerId) {
        Integer targetId = targetIdsByOwnerId.get(ownerId);
        return targetId == null ? null : sessionsByTargetId.get(targetId);
    }

    private static CaptureSnapshot removeOwnedSession(Character owner) {
        if (owner == null) {
            return null;
        }
        synchronized (sessionLock) {
            Integer targetId = targetIdsByOwnerId.remove(owner.getId());
            if (targetId == null) {
                return null;
            }
            CaptureSession session = sessionsByTargetId.remove(targetId);
            if (session == null) {
                return null;
            }
            synchronized (session) {
                session.active = false;
                List<CaptureRecord> records = List.copyOf(session.records);
                return new CaptureSnapshot(
                        session.ownerName,
                        session.targetName,
                        session.targetId,
                        session.startMapId,
                        session.startedAt,
                        Instant.now(),
                        session.packetLimit,
                        records);
            }
        }
    }

    private static Path writeReport(CaptureSnapshot capture) throws IOException {
        Path directory = Path.of(System.getProperty(
                "agents.movement.capture.dir",
                Path.of("logs", "agent-movement-captures").toString()));
        Files.createDirectories(directory);

        String safeName = capture.targetName.replaceAll("[^A-Za-z0-9._-]", "_");
        Path report = directory.resolve("climb-" + safeName + "-"
                + filenameTime.format(capture.startedAt) + ".log");
        Files.writeString(report, formatReport(capture), StandardCharsets.UTF_8);
        return report;
    }

    private static String formatReport(CaptureSnapshot capture) {
        StringBuilder output = new StringBuilder(8192);
        output.append("# Cosmic movement capture\n")
                .append("owner=").append(capture.ownerName).append('\n')
                .append("target=").append(capture.targetName)
                .append(" id=").append(capture.targetId).append('\n')
                .append("startMap=").append(capture.startMapId).append('\n')
                .append("started=").append(capture.startedAt).append('\n')
                .append("stopped=").append(capture.stoppedAt).append('\n')
                .append("packets=").append(capture.records.size())
                .append(" limit=").append(capture.packetLimit).append("\n\n");

        for (CaptureRecord record : capture.records) {
            output.append("packet[").append(record.sequence).append("] +")
                    .append(record.elapsedMillis).append("ms source=").append(record.source)
                    .append(" map=").append(record.mapId)
                    .append(" serverPos=").append(formatPoint(record.serverPosition))
                    .append(" serverStance=").append(record.serverStance)
                    .append(" bytes=").append(record.movementData.length).append('\n')
                    .append("  raw: ").append(MovementPacketDecoder.hex(record.movementData)).append('\n');
            for (String fragment : MovementPacketDecoder.decode(record.movementData)) {
                output.append("  ").append(fragment).append('\n');
            }
            output.append('\n');
        }
        return output.toString();
    }

    private static String formatPoint(Point point) {
        return point == null ? "null" : "(" + point.x + "," + point.y + ")";
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static final class CaptureSession {
        private final String ownerName;
        private final String targetName;
        private final int targetId;
        private final int startMapId;
        private final Instant startedAt;
        private final long startedNanos;
        private final int packetLimit;
        private final List<CaptureRecord> records = new ArrayList<>();
        private boolean active = true;

        private CaptureSession(Character owner, Character target, int packetLimit) {
            this.ownerName = owner.getName();
            this.targetName = target.getName();
            this.targetId = target.getId();
            this.startMapId = target.getMapId();
            this.startedAt = Instant.now();
            this.startedNanos = System.nanoTime();
            this.packetLimit = packetLimit;
        }
    }

    private record CaptureRecord(int sequence,
                                 long elapsedMillis,
                                 Source source,
                                 int mapId,
                                 Point serverPosition,
                                 int serverStance,
                                 byte[] movementData) {
    }

    private record CaptureSnapshot(String ownerName,
                                   String targetName,
                                   int targetId,
                                   int startMapId,
                                   Instant startedAt,
                                   Instant stoppedAt,
                                   int packetLimit,
                                   List<CaptureRecord> records) {
    }
}
