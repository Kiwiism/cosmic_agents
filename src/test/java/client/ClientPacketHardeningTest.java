package client;

import io.netty.buffer.Unpooled;
import net.packet.ByteBufInPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientPacketHardeningTest {
    @Test
    void shouldCapMalformedPacketPreview() {
        byte[] content = new byte[1_000];

        assertEquals(256 * 3 - 1, Client.packetPreview(content).length());
    }

    @Test
    void shouldReadOnlyTheRequestedPacketPreviewBytes() {
        ByteBufInPacket packet = new ByteBufInPacket(Unpooled.wrappedBuffer(new byte[]{1, 2, 3, 4, 5}));

        assertArrayEquals(new byte[]{1, 2, 3}, packet.getBytes(3));
        assertEquals(0, packet.getPosition());
        assertEquals(5, packet.available());
    }
}
