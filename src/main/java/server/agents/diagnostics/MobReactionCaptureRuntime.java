package server.agents.diagnostics;

import client.BotClient;
import client.Character;
import client.Client;
import net.opcodes.SendOpcode;
import net.packet.InPacket;
import net.packet.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.life.Monster;
import server.maps.MapleMap;

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
 * Bounded, GM-initiated capture of the packets and server state surrounding hits on one monster.
 *
 * <p>This is deliberately separate from the general monitored-character logger. The hot-path hooks
 * do no packet copying unless a capture is active on the exact map, and only a small allow-list of
 * attack, monster movement, damage, and controller packets can be retained.</p>
 */
public final class MobReactionCaptureRuntime {
    public static final int DEFAULT_EVENT_LIMIT = 500;
    public static final int MAX_EVENT_LIMIT = 2_000;
    private static final int MAX_RETAINED_PACKET_BYTES = config.AgentTuning.intValue("server.agents.diagnostics.MobReactionCaptureRuntime.MAX_RETAINED_PACKET_BYTES");

    private static final Logger log = LoggerFactory.getLogger(MobReactionCaptureRuntime.class);
    private static final Object sessionLock = new Object();
    private static final Map<MapleMap, CaptureSession> sessionsByMap = new ConcurrentHashMap<>();
    private static final Map<Integer, CaptureSession> sessionsByOwnerId = new ConcurrentHashMap<>();
    private static final DateTimeFormatter filenameTime = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneId.systemDefault());

    private MobReactionCaptureRuntime() {
    }

    public record OperationResult(boolean success, String message) {
    }

    public record Status(boolean active,
                         int mapId,
                         int monsterOid,
                         int monsterId,
                         String monsterName,
                         String controllerName,
                         int eventCount,
                         int eventLimit,
                         boolean limitReached,
                         long elapsedMillis) {
        public static Status inactive() {
            return new Status(false, 0, 0, 0, "", "", 0, 0, false, 0L);
        }
    }

    public record StopResult(boolean success, String message, Path reportPath, int eventCount) {
    }

    public static OperationResult start(Character owner, MapleMap map, Monster monster, int eventLimit) {
        if (owner == null || map == null || monster == null) {
            return new OperationResult(false, "Capture owner, map, and monster must all be present.");
        }
        if (eventLimit < 1 || eventLimit > MAX_EVENT_LIMIT) {
            return new OperationResult(false,
                    "Event limit must be between 1 and " + MAX_EVENT_LIMIT + ".");
        }
        if (owner.getMap() != map) {
            return new OperationResult(false, "You must be on the map being captured.");
        }
        if (map.getMonsterByOid(monster.getObjectId()) != monster) {
            return new OperationResult(false, "That monster is no longer present on this map.");
        }

        synchronized (sessionLock) {
            if (sessionsByOwnerId.containsKey(owner.getId())) {
                return new OperationResult(false,
                        "You already have an active mob capture; stop or clear it first.");
            }
            CaptureSession existing = sessionsByMap.get(map);
            if (existing != null) {
                return new OperationResult(false,
                        "This map is already being captured by " + existing.ownerName + ".");
            }

            CaptureSession session = new CaptureSession(owner, map, monster, eventLimit);
            sessionsByMap.put(map, session);
            sessionsByOwnerId.put(owner.getId(), session);
        }

        return new OperationResult(true,
                "Mob reaction capture started for " + safeMonsterName(monster) + " (OID "
                        + monster.getObjectId() + ", limit " + eventLimit + ").");
    }

    /** Records an exact decrypted client payload. The supplied InPacket is positioned after its opcode. */
    public static void recordInbound(Client client, int opcode, InPacket packet) {
        if (sessionsByMap.isEmpty() || client == null || packet == null) {
            return;
        }
        Character sender = client.getPlayer();
        if (sender == null) {
            return;
        }
        MapleMap map = sender.getMap();
        if (map == null) {
            return;
        }
        CaptureSession session = sessionsByMap.get(map);
        if (session == null || !canRecord(session)) {
            return;
        }

        byte[] fullPacket = withOpcode(opcode, packet.getBytes());
        if (!MobReactionPacketDecoder.isRelevantInbound(opcode, fullPacket, session.monsterOid)) {
            return;
        }
        addPacket(session, EventKind.CLIENT_TO_SERVER, opcode, describeCharacter(sender), "-", fullPacket);
    }

    /** Records whitelisted packets at the final per-client delivery boundary. */
    public static void recordOutbound(Client client, Packet packet) {
        if (sessionsByMap.isEmpty() || client == null || packet == null) {
            return;
        }
        Character recipient = client.getPlayer();
        if (recipient == null) {
            return;
        }
        MapleMap map = recipient.getMap();
        if (map == null) {
            return;
        }
        CaptureSession session = sessionsByMap.get(map);
        if (session == null || !canRecord(session)) {
            return;
        }

        byte[] fullPacket = packet.getBytes();
        Integer opcode = readUnsignedShort(fullPacket, 0);
        if (opcode == null
                || !MobReactionPacketDecoder.isRelevantOutbound(opcode, fullPacket, session.monsterOid)) {
            return;
        }
        addPacket(session, EventKind.SERVER_TO_CLIENT, opcode,
                describeOutboundSource(map, opcode, fullPacket),
                describeCharacter(recipient), fullPacket);
    }

    /** Records the server's broadcast intent once, including broadcasts with no eligible recipient. */
    public static void recordBroadcast(MapleMap map, Character source, Packet packet) {
        if (sessionsByMap.isEmpty() || map == null || packet == null) {
            return;
        }
        CaptureSession session = sessionsByMap.get(map);
        if (session == null || !canRecord(session)) {
            return;
        }

        byte[] fullPacket = packet.getBytes();
        Integer opcode = readUnsignedShort(fullPacket, 0);
        if (opcode == null
                || !MobReactionPacketDecoder.isRelevantOutbound(opcode, fullPacket, session.monsterOid)) {
            return;
        }
        addPacket(session, EventKind.SERVER_BROADCAST, opcode,
                source == null ? "server" : describeCharacterType(source),
                "map-broadcast", fullPacket);
    }

    /** Adds a server-side anchor after accepted HP damage, independent of any visual packet. */
    public static void recordDamage(Character attacker, Monster monster, int requestedDamage, boolean killed) {
        if (sessionsByMap.isEmpty() || monster == null) {
            return;
        }
        MapleMap map = monster.getMap();
        if (map == null) {
            return;
        }
        CaptureSession session = sessionsByMap.get(map);
        if (session == null || session.monster != monster) {
            return;
        }
        String attackerDescription = describeCharacter(attacker);
        if (attacker != null && attacker.getClient() instanceof BotClient) {
            attackerDescription += "[Agent]";
        }
        addEvent(session, EventKind.SERVER_DAMAGE, null, attackerDescription, "-", null,
                "requestedDamage=" + requestedDamage + " remainingHp=" + monster.getHp()
                        + " killed=" + killed);
    }

    public static OperationResult mark(Character owner, String label) {
        if (owner == null) {
            return new OperationResult(false, "No active mob capture.");
        }
        CaptureSession session = sessionsByOwnerId.get(owner.getId());
        if (session == null) {
            return new OperationResult(false, "You do not have an active mob capture.");
        }
        String normalized = label == null ? "" : label.strip();
        if (normalized.isEmpty()) {
            return new OperationResult(false, "Marker text cannot be empty.");
        }
        if (normalized.length() > 120) {
            normalized = normalized.substring(0, 120);
        }
        boolean added = addEvent(session, EventKind.MARK, null, describeCharacter(owner), "-", null,
                normalized.replace('\r', ' ').replace('\n', ' '));
        return added
                ? new OperationResult(true, "Capture marker added: " + normalized)
                : new OperationResult(false, "Capture event limit has been reached; stop to write the report.");
    }

    public static Status status(Character owner) {
        if (owner == null) {
            return Status.inactive();
        }
        CaptureSession session = sessionsByOwnerId.get(owner.getId());
        if (session == null) {
            return Status.inactive();
        }
        synchronized (session) {
            Character controller = session.monster.getController();
            return new Status(
                    session.active,
                    session.mapId,
                    session.monsterOid,
                    session.monsterId,
                    session.monsterName,
                    controller == null ? "none" : controller.getName(),
                    session.events.size(),
                    session.eventLimit,
                    session.events.size() >= session.eventLimit,
                    elapsedMillis(session.startedNanos));
        }
    }

    public static StopResult stop(Character owner) {
        CaptureSnapshot snapshot = removeOwnedSession(owner);
        if (snapshot == null) {
            return new StopResult(false, "You do not have an active mob capture.", null, 0);
        }

        try {
            Path report = writeReport(snapshot).toAbsolutePath();
            log.info("Mob reaction capture for OID {} wrote {} event(s) to {}",
                    snapshot.monsterOid, snapshot.events.size(), report);
            return new StopResult(true,
                    "Captured " + snapshot.events.size() + " mob reaction event(s).",
                    report,
                    snapshot.events.size());
        } catch (IOException failure) {
            log.error("Unable to write mob reaction capture for OID {}", snapshot.monsterOid, failure);
            return new StopResult(false,
                    "Captured " + snapshot.events.size() + " event(s), but report writing failed: "
                            + failure.getMessage(),
                    null,
                    snapshot.events.size());
        }
    }

    public static OperationResult clear(Character owner) {
        CaptureSnapshot removed = removeOwnedSession(owner);
        if (removed == null) {
            return new OperationResult(false, "You do not have an active mob capture.");
        }
        return new OperationResult(true,
                "Discarded " + removed.events.size() + " captured event(s) for OID "
                        + removed.monsterOid + ".");
    }

    /** Finalizes an abandoned capture when its owning GM disconnects or changes channel. */
    public static void stopIfOwned(Character owner) {
        if (owner == null || !sessionsByOwnerId.containsKey(owner.getId())) {
            return;
        }
        StopResult result = stop(owner);
        if (!result.success()) {
            log.warn("Unable to finalize disconnected owner's mob capture: {}", result.message());
        }
    }

    private static void addPacket(CaptureSession session,
                                  EventKind kind,
                                  int opcode,
                                  String actor,
                                  String recipient,
                                  byte[] fullPacket) {
        int retainedLength = Math.min(fullPacket.length, MAX_RETAINED_PACKET_BYTES);
        String detail = retainedLength == fullPacket.length
                ? ""
                : "packetTruncated=true originalBytes=" + fullPacket.length
                        + " retainedBytes=" + retainedLength;
        addEvent(session, kind, opcode, actor, recipient,
                Arrays.copyOf(fullPacket, retainedLength), detail);
    }

    private static boolean canRecord(CaptureSession session) {
        synchronized (session) {
            return session.active && session.events.size() < session.eventLimit;
        }
    }

    private static boolean addEvent(CaptureSession session,
                                    EventKind kind,
                                    Integer opcode,
                                    String actor,
                                    String recipient,
                                    byte[] fullPacket,
                                    String detail) {
        synchronized (session) {
            if (!session.active || session.events.size() >= session.eventLimit) {
                return false;
            }
            session.events.add(new CaptureEvent(
                    session.events.size() + 1,
                    elapsedMillis(session.startedNanos),
                    Instant.now(),
                    kind,
                    opcode,
                    actor,
                    recipient,
                    snapshotMonster(session.monster),
                    fullPacket,
                    detail));
            return true;
        }
    }

    private static CaptureSnapshot removeOwnedSession(Character owner) {
        if (owner == null) {
            return null;
        }
        synchronized (sessionLock) {
            CaptureSession session = sessionsByOwnerId.remove(owner.getId());
            if (session == null) {
                return null;
            }
            sessionsByMap.remove(session.map, session);
            synchronized (session) {
                session.active = false;
                return new CaptureSnapshot(
                        session.ownerName,
                        session.mapId,
                        session.monsterOid,
                        session.monsterId,
                        session.monsterName,
                        session.startedAt,
                        Instant.now(),
                        session.eventLimit,
                        session.initialState,
                        List.copyOf(session.events));
            }
        }
    }

    private static Path writeReport(CaptureSnapshot capture) throws IOException {
        Path directory = Path.of(System.getProperty(
                "agents.mob.reaction.capture.dir",
                Path.of("logs", "mob-reaction-captures").toString()));
        Files.createDirectories(directory);

        String safeName = capture.monsterName.replaceAll("[^A-Za-z0-9._-]", "_");
        Path report = directory.resolve("mob-reaction-" + safeName + "-mid" + capture.monsterId
                + "-oid" + capture.monsterOid + "-" + filenameTime.format(capture.startedAt) + ".log");
        Files.writeString(report, formatReport(capture), StandardCharsets.UTF_8);
        return report;
    }

    private static String formatReport(CaptureSnapshot capture) {
        StringBuilder output = new StringBuilder(32_768);
        output.append("# Cosmic mob reaction packet capture\n")
                .append("# rawPacket is the decrypted Maple payload, including its 2-byte little-endian opcode.\n")
                .append("# Packets above ").append(MAX_RETAINED_PACKET_BYTES)
                .append(" bytes are explicitly marked and truncated in this diagnostic report.\n")
                .append("owner=").append(capture.ownerName).append('\n')
                .append("map=").append(capture.mapId).append('\n')
                .append("monster=").append(capture.monsterName)
                .append(" templateId=").append(capture.monsterId)
                .append(" oid=").append(capture.monsterOid).append('\n')
                .append("started=").append(capture.startedAt).append('\n')
                .append("stopped=").append(capture.stoppedAt).append('\n')
                .append("events=").append(capture.events.size())
                .append(" limit=").append(capture.eventLimit).append('\n')
                .append("initialState: ").append(formatState(capture.initialState)).append("\n\n");

        for (CaptureEvent event : capture.events) {
            output.append("event[").append(event.sequence).append("] +")
                    .append(event.elapsedMillis).append("ms at=").append(event.recordedAt)
                    .append(" kind=").append(event.kind);
            if (event.opcode != null) {
                String opcodeName = event.kind == EventKind.CLIENT_TO_SERVER
                        ? MobReactionPacketDecoder.recvOpcodeName(event.opcode)
                        : MobReactionPacketDecoder.sendOpcodeName(event.opcode);
                output.append(" opcode=0x").append(String.format("%04X", event.opcode))
                        .append('(').append(opcodeName).append(')');
            }
            output.append(" actor=").append(event.actor)
                    .append(" recipient=").append(event.recipient).append('\n')
                    .append("  state: ").append(formatState(event.monsterState)).append('\n');
            if (!event.detail.isEmpty()) {
                output.append("  detail: ").append(event.detail).append('\n');
            }
            if (event.fullPacket != null) {
                output.append("  rawPacket[").append(event.fullPacket.length).append("]: ")
                        .append(MovementPacketDecoder.hex(event.fullPacket)).append('\n');
                List<String> decoded = event.kind == EventKind.CLIENT_TO_SERVER
                        ? MobReactionPacketDecoder.decodeInbound(event.opcode, event.fullPacket, capture.monsterOid)
                        : MobReactionPacketDecoder.decodeOutbound(event.opcode, event.fullPacket, capture.monsterOid);
                for (String line : decoded) {
                    output.append("  decoded: ").append(line).append('\n');
                }
            }
            output.append('\n');
        }
        return output.toString();
    }

    private static MonsterState snapshotMonster(Monster monster) {
        Point position = monster.getPosition();
        Character controller = monster.getController();
        return new MonsterState(
                position == null ? null : new Point(position),
                monster.getStance(),
                monster.getFh(),
                monster.getHp(),
                controller == null ? 0 : controller.getId(),
                controller == null ? "none" : controller.getName(),
                monster.isControllerHasAggro(),
                monster.isControllerKnowsAboutAggro());
    }

    private static String formatState(MonsterState state) {
        return "pos=" + formatPoint(state.position)
                + " stance=" + state.stance
                + " fh=" + state.foothold
                + " hp=" + state.hp
                + " controller=" + state.controllerName + "#" + state.controllerId
                + " controllerHasAggro=" + state.controllerHasAggro
                + " controllerKnowsAggro=" + state.controllerKnowsAggro;
    }

    private static byte[] withOpcode(int opcode, byte[] body) {
        byte[] fullPacket = new byte[Short.BYTES + body.length];
        fullPacket[0] = (byte) opcode;
        fullPacket[1] = (byte) (opcode >>> 8);
        System.arraycopy(body, 0, fullPacket, Short.BYTES, body.length);
        return fullPacket;
    }

    private static Integer readUnsignedShort(byte[] data, int offset) {
        if (data == null || offset < 0 || data.length - offset < Short.BYTES) {
            return null;
        }
        return Byte.toUnsignedInt(data[offset]) | Byte.toUnsignedInt(data[offset + 1]) << 8;
    }

    private static Integer readInt(byte[] data, int offset) {
        if (data == null || offset < 0 || data.length - offset < Integer.BYTES) {
            return null;
        }
        return Byte.toUnsignedInt(data[offset])
                | Byte.toUnsignedInt(data[offset + 1]) << 8
                | Byte.toUnsignedInt(data[offset + 2]) << 16
                | Byte.toUnsignedInt(data[offset + 3]) << 24;
    }

    private static String describeCharacter(Character character) {
        return character == null ? "none#0" : character.getName() + "#" + character.getId();
    }

    private static String describeCharacterType(Character character) {
        String description = describeCharacter(character);
        return character != null && character.getClient() instanceof BotClient
                ? description + "[Agent]"
                : description;
    }

    private static String describeOutboundSource(MapleMap map, int opcode, byte[] fullPacket) {
        if (opcode != SendOpcode.CLOSE_RANGE_ATTACK.getValue()
                && opcode != SendOpcode.RANGED_ATTACK.getValue()
                && opcode != SendOpcode.MAGIC_ATTACK.getValue()) {
            return "server";
        }
        Integer characterId = readInt(fullPacket, Short.BYTES);
        if (characterId == null) {
            return "unknown-attacker";
        }
        Character attacker = map == null ? null : map.getCharacterById(characterId);
        if (attacker == null) {
            return "character#" + characterId;
        }
        return describeCharacterType(attacker);
    }

    private static String safeMonsterName(Monster monster) {
        String name = monster.getName();
        return name == null || name.isBlank() ? "monster-" + monster.getId() : name;
    }

    private static String formatPoint(Point point) {
        return point == null ? "null" : "(" + point.x + "," + point.y + ")";
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private enum EventKind {
        CLIENT_TO_SERVER,
        SERVER_BROADCAST,
        SERVER_TO_CLIENT,
        SERVER_DAMAGE,
        MARK
    }

    private static final class CaptureSession {
        private final String ownerName;
        private final MapleMap map;
        private final Monster monster;
        private final int mapId;
        private final int monsterOid;
        private final int monsterId;
        private final String monsterName;
        private final Instant startedAt;
        private final long startedNanos;
        private final int eventLimit;
        private final MonsterState initialState;
        private final List<CaptureEvent> events = new ArrayList<>();
        private boolean active = true;

        private CaptureSession(Character owner, MapleMap map, Monster monster, int eventLimit) {
            this.ownerName = owner.getName();
            this.map = map;
            this.monster = monster;
            this.mapId = map.getId();
            this.monsterOid = monster.getObjectId();
            this.monsterId = monster.getId();
            this.monsterName = safeMonsterName(monster);
            this.startedAt = Instant.now();
            this.startedNanos = System.nanoTime();
            this.eventLimit = eventLimit;
            this.initialState = snapshotMonster(monster);
        }
    }

    private record CaptureEvent(int sequence,
                                long elapsedMillis,
                                Instant recordedAt,
                                EventKind kind,
                                Integer opcode,
                                String actor,
                                String recipient,
                                MonsterState monsterState,
                                byte[] fullPacket,
                                String detail) {
    }

    private record MonsterState(Point position,
                                int stance,
                                int foothold,
                                int hp,
                                int controllerId,
                                String controllerName,
                                boolean controllerHasAggro,
                                boolean controllerKnowsAggro) {
    }

    private record CaptureSnapshot(String ownerName,
                                   int mapId,
                                   int monsterOid,
                                   int monsterId,
                                   String monsterName,
                                   Instant startedAt,
                                   Instant stoppedAt,
                                   int eventLimit,
                                   MonsterState initialState,
                                   List<CaptureEvent> events) {
    }
}
