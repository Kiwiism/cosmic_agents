package server.agents.observer.protocol;

import server.agents.capabilities.navigation.AgentMapGraphService;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationTraceSnapshot;
import server.agents.capabilities.combat.AgentCombatTargetTraceSnapshot;
import server.observer.protocol.ObserverProtocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ObserverNavGraphProtocol {
    public static final int VERSION = ObserverProtocol.VERSION;
    public static final int ACTION_SNAPSHOT = 0;
    public static final int ACTION_ROUTE = 1;
    public static final int ACTION_AGENT_TRACE = 2;
    public static final int ACTION_AGENT_TARGET = 3;
    public static final int STATUS_READY = 0;
    public static final int STATUS_WARMING = 1;
    public static final int STATUS_TOO_LARGE = 2;
    public static final int STATUS_ROUTE = 3;
    public static final int STATUS_INVALID_ROUTE = 4;
    public static final int STATUS_AGENT_TRACE = 5;
    public static final int STATUS_AGENT_UNAVAILABLE = 6;
    public static final int STATUS_AGENT_DISABLED = 7;
    public static final int STATUS_AGENT_TARGET = 8;
    public static final int STATUS_AGENT_TARGET_UNAVAILABLE = 9;
    public static final int STATUS_AGENT_TARGET_DISABLED = 10;
    public static final int CHUNK_BYTES = 24 * 1024;
    public static final int MAX_PAYLOAD_BYTES = 2 * 1024 * 1024;

    private static final int PAYLOAD_MAGIC = 0x3147564E;
    private static final int MAX_REGIONS = config.AgentTuning.intValue(
            "server.agents.observer.protocol.ObserverNavGraphProtocol.MAX_REGIONS");
    private static final int MAX_SEGMENTS = config.AgentTuning.intValue(
            "server.agents.observer.protocol.ObserverNavGraphProtocol.MAX_SEGMENTS");
    private static final int MAX_EDGES = config.AgentTuning.intValue(
            "server.agents.observer.protocol.ObserverNavGraphProtocol.MAX_EDGES");
    private static final int MAX_NPCS = config.AgentTuning.intValue(
            "server.agents.observer.protocol.ObserverNavGraphProtocol.MAX_NPCS");
    private static final int MAX_PORTALS = config.AgentTuning.intValue(
            "server.agents.observer.protocol.ObserverNavGraphProtocol.MAX_PORTALS");
    private static final int MAX_STRING_BYTES = config.AgentTuning.intValue(
            "server.agents.observer.protocol.ObserverNavGraphProtocol.MAX_STRING_BYTES");

    private ObserverNavGraphProtocol() {
    }

    public static byte[] encode(AgentMapGraphService.MapGraphView view) {
        requireCount("regions", view.regions().size(), MAX_REGIONS);
        requireCount("edges", view.edges().size(), MAX_EDGES);
        requireCount("npcs", view.npcs().size(), MAX_NPCS);
        requireCount("portals", view.portals().size(), MAX_PORTALS);

        int segmentCount = view.regions().stream()
                .mapToInt(region -> region.segments().size())
                .sum();
        requireCount("segments", segmentCount, MAX_SEGMENTS);

        Writer writer = new Writer();
        writer.writeInt(PAYLOAD_MAGIC);
        writer.writeString(view.name());
        writer.writeInt(view.bounds().minX());
        writer.writeInt(view.bounds().minY());
        writer.writeInt(view.bounds().maxX());
        writer.writeInt(view.bounds().maxY());

        writer.writeInt(view.regions().size());
        for (AgentMapGraphService.RegionView region : view.regions()) {
            writer.writeInt(region.id());
            writer.writeByte(regionKind(region));
            writer.writeInt(region.minX());
            writer.writeInt(region.minY());
            writer.writeInt(region.maxX());
            writer.writeInt(region.maxY());
            writer.writeInt(region.centerX());
            writer.writeInt(region.centerY());
            writer.writeShort(region.segments().size());
            for (List<Integer> segment : region.segments()) {
                if (segment.size() != 4) {
                    throw new IllegalArgumentException("Navigation segment must have four coordinates");
                }
                segment.forEach(writer::writeInt);
            }
        }

        writer.writeInt(view.edges().size());
        for (AgentMapGraphService.EdgeView edge : view.edges()) {
            writeEdge(writer, edge);
        }

        writer.writeShort(view.npcs().size());
        for (AgentMapGraphService.NpcView npc : view.npcs()) {
            writer.writeInt(npc.x());
            writer.writeInt(npc.y());
            writer.writeString(npc.name());
        }

        writer.writeShort(view.portals().size());
        for (AgentMapGraphService.PortalView portal : view.portals()) {
            writer.writeInt(portal.x());
            writer.writeInt(portal.y());
            writer.writeInt(portal.targetMapId());
            writer.writeByte(portalKind(portal.kind()));
            writer.writeString(portal.name());
        }

        byte[] payload = writer.toByteArray();
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Navigation graph payload exceeds limit");
        }
        return payload;
    }

    public static byte[] encodeRoute(AgentMapGraphService.RouteView route) {
        requireCount("route edges", route.path().size(), MAX_EDGES);
        Writer writer = new Writer();
        writer.writeInt(0x3152564E);
        writer.writeInt(route.fromRegion());
        writer.writeInt(route.toRegion());
        writer.writeByte("exhaustive".equals(route.mode()) ? 1 : 0);
        writer.writeByte(route.reached() ? 1 : 0);
        writer.writeByte(route.bestEffort() ? 1 : 0);
        writer.writeByte(route.capped() ? 1 : 0);
        writer.writeInt(route.finalRegion());
        writer.writeInt(route.cost() == null ? -1 : route.cost());
        writer.writeInt(route.expandedNodes());
        writer.writeInt((int) Math.min(
                Integer.MAX_VALUE,
                Math.round(route.elapsedMs() * 1_000.0d)));
        writer.writeInt(route.path().size());
        route.path().forEach(edge -> writeEdge(writer, edge));
        return writer.toByteArray();
    }

    public static byte[] encodeAgentTrace(AgentNavigationTraceSnapshot trace,
                                          boolean includePath) {
        requireCount("agent route edges", trace.path().size(), MAX_EDGES);
        if (trace.transitions().size() > 64) {
            throw new IllegalArgumentException("Navigation trace history exceeds limit");
        }
        Writer writer = new Writer();
        writer.writeInt(0x3154564E);
        writer.writeInt(trace.characterId());
        writer.writeString(trace.characterName());
        writer.writeLong(trace.sampledAtMs());
        writer.writeLong(trace.routeRevision());
        writer.writeLong(trace.plannedAtMs());
        writer.writeString(trace.routeSource());
        writer.writeString(trace.routeReason());
        writer.writeString(trace.objectiveId());
        writePosition(writer, trace.agentPosition());
        writer.writeInt(trace.currentRegionId());
        writer.writeInt(trace.targetRegionId());
        writePosition(writer, trace.targetPosition());
        writePosition(writer, trace.waypoint());
        writer.writeByte(trace.preciseWaypoint() ? 1 : 0);
        writer.writeString(trace.decision());
        writer.writeString(trace.blockReason());
        writer.writeInt(trace.activeEdgeIndex());
        writer.writeInt(trace.routeCost());
        writer.writeInt(trace.expandedNodes());
        writer.writeLong(trace.elapsedMicroseconds());
        writer.writeByte(trace.reached() ? 1 : 0);
        writer.writeByte(trace.bestEffort() ? 1 : 0);
        writer.writeByte(trace.capped() ? 1 : 0);
        writer.writeInt(trace.stuckMs());
        writer.writeInt(trace.recoveryCooldownMs());
        writer.writeLong(trace.lastProgressAtMs());
        writer.writeString(trace.loopKind());
        writer.writeByte(trace.suppressedEdge() == null ? 0 : 1);
        if (trace.suppressedEdge() != null) {
            writeTraceEdge(writer, trace.suppressedEdge());
        }
        writer.writeLong(trace.suppressedUntilMs());
        writer.writeInt(trace.recoveryCount());
        writer.writeLong(trace.lastRecoveryAtMs());
        writer.writeString(trace.lastRecoveryType());
        writer.writeString(trace.verticalStage());
        writer.writeShort(trace.transitions().size());
        for (AgentNavigationTraceSnapshot.Transition transition : trace.transitions()) {
            writer.writeInt(transition.fromRegionId());
            writer.writeInt(transition.toRegionId());
            writer.writeLong(transition.timestampMs());
        }
        writer.writeByte(includePath ? 1 : 0);
        if (includePath) {
            writer.writeInt(trace.path().size());
            trace.path().forEach(edge -> writeTraceEdge(writer, edge));
        }
        return writer.toByteArray();
    }

    public static byte[] encodeAgentTarget(AgentCombatTargetTraceSnapshot trace) {
        Writer writer = new Writer();
        writer.writeInt(0x31544754);
        writer.writeInt(trace.characterId());
        writer.writeString(trace.characterName());
        writer.writeLong(trace.sampledAtMs());
        writePosition(writer, trace.agentPosition());
        writer.writeByte(trace.hasTarget() ? 1 : 0);
        if (trace.hasTarget()) {
            writer.writeInt(trace.targetObjectId());
            writer.writeInt(trace.targetMobId());
            writer.writeString(trace.targetName());
            writePosition(writer, trace.targetPosition());
            writer.writeByte(trace.targetHpPercent());
            writer.writeString(trace.action());
            writer.writeString(trace.reasonCode());
            writer.writeString(trace.reasonText());
            writer.writeString(trace.objectiveId());
            writer.writeString(trace.candidateClass());
            writer.writeLong(trace.selectedAtMs());
            writer.writeInt(trace.targetSwitchCount());
        }
        return writer.toByteArray();
    }

    public static List<byte[]> chunks(byte[] payload) {
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Navigation graph payload exceeds limit");
        }
        List<byte[]> chunks = new ArrayList<>(
                Math.max(1, (payload.length + CHUNK_BYTES - 1) / CHUNK_BYTES));
        for (int offset = 0; offset < payload.length; offset += CHUNK_BYTES) {
            chunks.add(Arrays.copyOfRange(
                    payload,
                    offset,
                    Math.min(payload.length, offset + CHUNK_BYTES)));
        }
        return List.copyOf(chunks);
    }

    public static int checksum(byte[] payload) {
        int hash = 0x811C9DC5;
        for (byte value : payload) {
            hash ^= value & 0xFF;
            hash *= 0x01000193;
        }
        return hash;
    }

    private static int regionKind(AgentMapGraphService.RegionView region) {
        if (!"rope".equals(region.kind())) {
            return 0;
        }
        return region.ladder() ? 2 : 1;
    }

    private static int portalKind(String kind) {
        return switch (kind) {
            case "in-map" -> 1;
            case "cross-map" -> 2;
            case "collision" -> 3;
            default -> 0;
        };
    }

    private static void writeEdge(Writer writer, AgentMapGraphService.EdgeView edge) {
        writer.writeByte(AgentNavigationGraph.EdgeType.valueOf(edge.type()).ordinal());
        writer.writeInt(edge.fromRegion());
        writer.writeInt(edge.toRegion());
        writer.writeInt(edge.cost());
        writer.writeInt(edge.launchStepX());
        writer.writeInt(edge.parallelCount());
        writer.writeInt(edge.fromX());
        writer.writeInt(edge.fromY());
        writer.writeInt(edge.toX());
        writer.writeInt(edge.toY());
    }

    private static void writePosition(Writer writer,
                                      AgentNavigationTraceSnapshot.Position position) {
        writer.writeByte(position.present() ? 1 : 0);
        if (position.present()) {
            writer.writeInt(position.x());
            writer.writeInt(position.y());
        }
    }

    private static void writePosition(Writer writer,
                                      AgentCombatTargetTraceSnapshot.Position position) {
        writer.writeByte(position.present() ? 1 : 0);
        if (position.present()) {
            writer.writeInt(position.x());
            writer.writeInt(position.y());
        }
    }

    private static void writeTraceEdge(Writer writer,
                                       AgentNavigationTraceSnapshot.Edge edge) {
        writer.writeByte(edge.type().ordinal());
        writer.writeInt(edge.fromRegionId());
        writer.writeInt(edge.toRegionId());
        writer.writeInt(edge.startX());
        writer.writeInt(edge.startY());
        writer.writeInt(edge.endX());
        writer.writeInt(edge.endY());
        writer.writeInt(edge.launchMinX());
        writer.writeInt(edge.launchMaxX());
        writer.writeInt(edge.launchStepX());
        writer.writeInt(edge.portalId());
        writer.writeInt(edge.ropeX());
        writer.writeInt(edge.ropeTopY());
        writer.writeInt(edge.ropeBottomY());
        writer.writeInt(edge.cost());
    }

    private static void requireCount(String name, int value, int maximum) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException(
                    "Navigation graph " + name + " exceeds limit");
        }
    }

    private static final class Writer {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        void writeByte(int value) {
            output.write(value);
        }

        void writeShort(int value) {
            output.write(value);
            output.write(value >>> 8);
        }

        void writeInt(int value) {
            output.write(value);
            output.write(value >>> 8);
            output.write(value >>> 16);
            output.write(value >>> 24);
        }

        void writeLong(long value) {
            writeInt((int) value);
            writeInt((int) (value >>> 32));
        }

        void writeString(String value) {
            byte[] bytes = (value == null ? "" : value)
                    .getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_STRING_BYTES) {
                throw new IllegalArgumentException(
                        "Navigation graph string exceeds limit");
            }
            writeShort(bytes.length);
            output.writeBytes(bytes);
        }

        byte[] toByteArray() {
            return output.toByteArray();
        }
    }
}
