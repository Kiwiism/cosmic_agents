package server.agents.diagnostics;

import client.Character;
import client.Client;
import config.YamlConfig;
import net.opcodes.SendOpcode;
import net.packet.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.life.Monster;
import tools.HexTool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Bounded outbound packet trace written only when a client drops shortly after changing maps. */
public final class MapTransitionPacketTraceRuntime {
    private static final Logger log = LoggerFactory.getLogger(MapTransitionPacketTraceRuntime.class);
    private static final long CAPTURE_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(15);
    private static final int MAX_EVENTS = 600;
    private static final int PREVIEW_BYTES = 512;
    private static final Map<Client, TraceSession> sessions = new ConcurrentHashMap<>();
    private static final Map<Integer, String> opcodeNames = opcodeNames();
    private static final DateTimeFormatter filenameTime = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneId.systemDefault());

    private MapTransitionPacketTraceRuntime() {
    }

    public static void begin(Client client, int fromMapId, int toMapId) {
        if (!enabled() || client == null) {
            return;
        }
        Character player = client.getPlayer();
        TraceSession session = new TraceSession(
                player == null ? "unknown" : player.getName(), fromMapId, toMapId);
        sessions.put(client, session);
        add(session, "BEGIN", "map transition requested");
    }

    public static void mark(Client client, String phase) {
        TraceSession session = active(client);
        if (session != null) {
            add(session, "MARK", phase);
        }
    }

    public static void markMonster(Client client, String phase, Monster monster) {
        TraceSession session = active(client);
        if (session == null || monster == null) {
            return;
        }
        Character controller = monster.getController();
        add(session, "MONSTER", phase
                + " oid=" + monster.getObjectId()
                + " template=" + monster.getId()
                + " pos=" + monster.getPosition()
                + " fh=" + monster.getFh()
                + " stance=" + monster.getStance()
                + " hp=" + monster.getHp() + "/" + monster.getMaxHp()
                + " authority=" + monster.getControlAuthority()
                + " controller=" + (controller == null ? "none" : controller.getName()));
    }

    public static void complete(Client client) {
        TraceSession session = active(client);
        if (session != null) {
            add(session, "COMPLETE", "client acknowledged destination field rebuild");
            session.completed = true;
        }
    }

    public static void recordOutbound(Client client, Packet packet) {
        TraceSession session = active(client);
        if (session == null || packet == null) {
            return;
        }
        byte[] bytes = packet.getBytes();
        int opcode = bytes.length >= 2
                ? (bytes[0] & 0xff) | ((bytes[1] & 0xff) << 8)
                : -1;
        int retained = Math.min(bytes.length, PREVIEW_BYTES);
        Character player = client.getPlayer();
        String detail = "opcode=0x" + String.format("%04X", opcode)
                + "(" + opcodeNames.getOrDefault(opcode, "UNKNOWN") + ")"
                + " bytes=" + bytes.length
                + " map=" + (player == null ? -1 : player.getMapId())
                + " changingMaps=" + (player != null && player.isChangingMaps())
                + " preview=" + HexTool.toHexString(Arrays.copyOf(bytes, retained));
        add(session, "OUT", detail);
    }

    public static void disconnected(Client client, String reason) {
        TraceSession session = sessions.remove(client);
        if (session == null || expired(session)) {
            return;
        }
        add(session, "DISCONNECT", reason);
        try {
            Path report = write(session);
            log.warn("Client {} disconnected {} ms after map transition; packet trace: {}",
                    session.characterName, elapsedMillis(session), report.toAbsolutePath());
        } catch (IOException failure) {
            log.error("Unable to write map-transition packet trace for {}",
                    session.characterName, failure);
        }
    }

    static void clearForTest() {
        sessions.clear();
    }

    private static TraceSession active(Client client) {
        if (client == null) {
            return null;
        }
        TraceSession session = sessions.get(client);
        if (session != null && expired(session)) {
            sessions.remove(client, session);
            return null;
        }
        return session;
    }

    private static void add(TraceSession session, String kind, String detail) {
        synchronized (session) {
            if (session.events.size() >= MAX_EVENTS) {
                return;
            }
            session.events.add(new TraceEvent(elapsedMillis(session), Instant.now(), kind, detail));
        }
    }

    private static boolean expired(TraceSession session) {
        return System.nanoTime() - session.startedNanos > CAPTURE_WINDOW_NANOS;
    }

    private static long elapsedMillis(TraceSession session) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - session.startedNanos);
    }

    private static Path write(TraceSession session) throws IOException {
        Path directory = Path.of(System.getProperty(
                "agents.map.transition.trace.dir",
                Path.of("logs", "map-transition-crashes").toString()));
        Files.createDirectories(directory);
        Path report = directory.resolve("map-transition-" + safeName(session.characterName)
                + "-" + session.fromMapId + "-to-" + session.toMapId
                + "-" + filenameTime.format(session.startedAt) + ".log");

        StringBuilder output = new StringBuilder(64_000);
        output.append("# Map transition packet trace\n")
                .append("# Packet bytes are decrypted payload previews; the first two bytes are the little-endian opcode.\n")
                .append("character=").append(session.characterName).append('\n')
                .append("fromMap=").append(session.fromMapId).append('\n')
                .append("toMap=").append(session.toMapId).append('\n')
                .append("started=").append(session.startedAt).append('\n')
                .append("transitionCompleted=").append(session.completed).append('\n')
                .append("events=").append(session.events.size()).append("\n\n");
        synchronized (session) {
            for (TraceEvent event : session.events) {
                output.append('+').append(event.elapsedMillis).append("ms ")
                        .append(event.at).append(' ')
                        .append(event.kind).append(' ')
                        .append(event.detail).append('\n');
            }
        }
        Files.writeString(report, output, StandardCharsets.UTF_8);
        return report;
    }

    private static String safeName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static Map<Integer, String> opcodeNames() {
        Map<Integer, String> names = new HashMap<>();
        for (SendOpcode opcode : SendOpcode.values()) {
            names.putIfAbsent(opcode.getValue(), opcode.name());
        }
        return Map.copyOf(names);
    }

    private static boolean enabled() {
        return YamlConfig.config != null && YamlConfig.config.server != null
                && YamlConfig.config.server.AGENT_MAP_TRANSITION_PACKET_DIAGNOSTICS;
    }

    private record TraceEvent(long elapsedMillis, Instant at, String kind, String detail) {
    }

    private static final class TraceSession {
        private final String characterName;
        private final int fromMapId;
        private final int toMapId;
        private final long startedNanos = System.nanoTime();
        private final Instant startedAt = Instant.now();
        private final List<TraceEvent> events = new ArrayList<>();
        private volatile boolean completed;

        private TraceSession(String characterName, int fromMapId, int toMapId) {
            this.characterName = characterName;
            this.fromMapId = fromMapId;
            this.toMapId = toMapId;
        }
    }
}
