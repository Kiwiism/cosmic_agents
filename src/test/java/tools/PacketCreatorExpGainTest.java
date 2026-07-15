package tools;

import io.netty.buffer.Unpooled;
import net.opcodes.SendOpcode;
import net.packet.ByteBufInPacket;
import net.packet.Packet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacketCreatorExpGainTest {
    @Test
    void eventBonusUsesDedicatedV83ExpBreakdownField() {
        Packet packet = PacketCreator.getShowExpGain(1_000, 50, 100, false, true, 250);
        ByteBufInPacket reader = new ByteBufInPacket(Unpooled.wrappedBuffer(packet.getBytes()));

        assertEquals(SendOpcode.SHOW_STATUS_INFO.getValue(), reader.readShort());
        assertEquals(3, reader.readByte());
        assertEquals(1, reader.readByte());
        assertEquals(1_000, reader.readInt());
        assertEquals(0, reader.readByte());
        assertEquals(250, reader.readInt());
    }
}
