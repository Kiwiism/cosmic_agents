package server.agents.observer.protocol;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.navigation.AgentMapGraphService;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationTraceSnapshot;
import server.agents.capabilities.combat.AgentCombatTargetTraceSnapshot;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObserverNavGraphProtocolTest {
    @Test
    void encodesBoundedLittleEndianSnapshot() {
        AgentMapGraphService.MapGraphView view = new AgentMapGraphService.MapGraphView(
                103000000,
                "Kerning City",
                7,
                new AgentMapGraphService.MovementProfileView(100, 100),
                List.of(new AgentMapGraphService.MovementProfileView(100, 100)),
                new AgentMapGraphService.Bounds(-500, -200, 900, 500),
                List.of(new AgentMapGraphService.RegionView(
                        12, "foothold", false,
                        -100, 20, 200, 80, 50, 50,
                        List.of(List.of(-100, 20, 200, 80)),
                        List.of())),
                List.of(new AgentMapGraphService.EdgeView(
                        "WALK", 12, 13, 300, 0, 1,
                        200, 80, 500, 80)),
                List.of(new AgentMapGraphService.NpcView(10, 20, "Nella")),
                List.of(new AgentMapGraphService.PortalView(
                        800, 50, "cross-map", 103000100, "east00")),
                List.of());

        byte[] payload = ObserverNavGraphProtocol.encode(view);
        ByteBuffer input = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(0x3147564E, input.getInt());
        assertEquals("Kerning City", readString(input));
        assertEquals(-500, input.getInt());
        assertEquals(-200, input.getInt());
        assertEquals(900, input.getInt());
        assertEquals(500, input.getInt());
        assertEquals(1, input.getInt());
        assertEquals(12, input.getInt());
        assertEquals(0, input.get() & 0xFF);
    }

    @Test
    void chunksRoundTripAndChecksumIsStable() {
        byte[] payload = new byte[ObserverNavGraphProtocol.CHUNK_BYTES * 2 + 17];
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) index;
        }

        List<byte[]> chunks = ObserverNavGraphProtocol.chunks(payload);
        byte[] joined = new byte[payload.length];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, joined, offset, chunk.length);
            offset += chunk.length;
        }

        assertEquals(3, chunks.size());
        assertArrayEquals(payload, joined);
        assertEquals(0x96A2BDDF, ObserverNavGraphProtocol.checksum(payload));
    }

    @Test
    void encodesRouteResultAndPath() {
        AgentMapGraphService.RouteView route = new AgentMapGraphService.RouteView(
                103000000,
                12,
                18,
                "normal",
                true,
                false,
                false,
                18,
                450,
                7,
                1.25d,
                List.of(new AgentMapGraphService.EdgeView(
                        "JUMP", 12, 18, 450, 3, 1,
                        20, 30, 80, 10)));

        ByteBuffer input = ByteBuffer.wrap(
                ObserverNavGraphProtocol.encodeRoute(route)
        ).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(0x3152564E, input.getInt());
        assertEquals(12, input.getInt());
        assertEquals(18, input.getInt());
        assertEquals(0, input.get() & 0xFF);
        assertEquals(1, input.get() & 0xFF);
        assertEquals(0, input.get() & 0xFF);
        assertEquals(0, input.get() & 0xFF);
        assertEquals(18, input.getInt());
        assertEquals(450, input.getInt());
        assertEquals(7, input.getInt());
        assertEquals(1_250, input.getInt());
        assertEquals(1, input.getInt());
        assertEquals(1, input.get() & 0xFF);
    }

    @Test
    void rejectsOversizedPayload() {
        byte[] payload = new byte[ObserverNavGraphProtocol.MAX_PAYLOAD_BYTES + 1];
        assertThrows(IllegalArgumentException.class,
                () -> ObserverNavGraphProtocol.chunks(payload));
    }

    @Test
    void encodesLiveAgentTraceWithOptionalPathDelta() {
        AgentNavigationTraceSnapshot.Edge edge =
                new AgentNavigationTraceSnapshot.Edge(
                        12, 18, AgentNavigationGraph.EdgeType.JUMP,
                        20, 30, 80, 10,
                        15, 25, 3, -1, 0, 0, 0, 450);
        AgentNavigationTraceSnapshot trace = new AgentNavigationTraceSnapshot(
                77, "BluePanda", 103000000, 2_000L,
                7, 100, 100, 4L, 1_900L,
                "NORMAL", "objective-route", "quest:1000",
                new AgentNavigationTraceSnapshot.Position(true, 20, 30),
                12, 18,
                new AgentNavigationTraceSnapshot.Position(true, 80, 10),
                new AgentNavigationTraceSnapshot.Position(true, 25, 25),
                true, "MOVE", "", List.of(edge), 0,
                450, 7, 1_250L, true, false, false,
                0, 0, 1_850L, "", null, 0L,
                1, 1_700L, "REROUTE", "",
                List.of(new AgentNavigationTraceSnapshot.Transition(
                        11, 12, 1_800L)));

        ByteBuffer full = ByteBuffer.wrap(
                ObserverNavGraphProtocol.encodeAgentTrace(trace, true)
        ).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x3154564E, full.getInt());
        assertEquals(77, full.getInt());
        assertEquals("BluePanda", readString(full));

        byte[] unchanged = ObserverNavGraphProtocol.encodeAgentTrace(trace, false);
        assertEquals(0, unchanged[unchanged.length - 1]);
    }

    @Test
    void encodesAgentCombatTargetIntent() {
        AgentCombatTargetTraceSnapshot trace = new AgentCombatTargetTraceSnapshot(
                77, "BluePanda", 103000000, 2_000L,
                new AgentCombatTargetTraceSnapshot.Position(true, 20, 30),
                true, 9001, 100100, "Blue Snail",
                new AgentCombatTargetTraceSnapshot.Position(true, 80, 10),
                75, "engage", "REQUIRED_LOCAL",
                "Required objective target on this platform", "quest:1000",
                "REQUIRED", 1_900L, 2);

        ByteBuffer input = ByteBuffer.wrap(
                ObserverNavGraphProtocol.encodeAgentTarget(trace)
        ).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(0x31544754, input.getInt());
        assertEquals(77, input.getInt());
        assertEquals("BluePanda", readString(input));
        assertEquals(2_000L, input.getLong());
        assertEquals(1, input.get() & 0xFF);
        assertEquals(20, input.getInt());
        assertEquals(30, input.getInt());
        assertEquals(1, input.get() & 0xFF);
        assertEquals(9001, input.getInt());
        assertEquals(100100, input.getInt());
        assertEquals("Blue Snail", readString(input));
    }

    private static String readString(ByteBuffer input) {
        int length = input.getShort() & 0xFFFF;
        byte[] value = new byte[length];
        input.get(value);
        return new String(value, StandardCharsets.UTF_8);
    }
}
