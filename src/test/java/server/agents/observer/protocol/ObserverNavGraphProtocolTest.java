package server.agents.observer.protocol;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.navigation.AgentMapGraphService;

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
    void rejectsOversizedPayload() {
        byte[] payload = new byte[ObserverNavGraphProtocol.MAX_PAYLOAD_BYTES + 1];
        assertThrows(IllegalArgumentException.class,
                () -> ObserverNavGraphProtocol.chunks(payload));
    }

    private static String readString(ByteBuffer input) {
        int length = input.getShort() & 0xFFFF;
        byte[] value = new byte[length];
        input.get(value);
        return new String(value, StandardCharsets.UTF_8);
    }
}
