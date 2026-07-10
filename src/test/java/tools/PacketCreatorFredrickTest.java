package tools;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.id.NpcId;
import net.opcodes.SendOpcode;
import net.packet.Packet;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class PacketCreatorFredrickTest {
    @Test
    void shouldCreateStructurallyValidEmptyStoragePacket() {
        Character chr = characterWithMerchantMeso(123_456);

        byte[] packet = PacketCreator.getFredrick(chr, List.of()).getBytes();

        assertEquals(25, packet.length);
        assertEquals(SendOpcode.FREDRICK.getValue(), unsignedShortLe(packet, 0));
        assertEquals(0x23, Byte.toUnsignedInt(packet[2]));
        assertEquals(NpcId.FREDRICK, intLe(packet, 3));
        assertEquals(32272, intLe(packet, 7));
        assertEquals(123_456, intLe(packet, 16));
        assertEquals(0, Byte.toUnsignedInt(packet[20]));
        assertEquals(0, Byte.toUnsignedInt(packet[21]));
        assertEquals(0, packet[22]);
        assertEquals(0, packet[23]);
        assertEquals(0, packet[24]);
    }

    @Test
    void shouldCreateStructurallyValidStoredItemPacket() {
        Character chr = characterWithMerchantMeso(0);
        Item item = new Item(2000000, (byte) 1, (short) 3);

        byte[] packet = PacketCreator.getFredrick(chr,
                List.of(new Pair<>(item, InventoryType.USE)),
                (out, storedItem) -> out.writeInt(storedItem.getItemId())).getBytes();

        assertEquals(SendOpcode.FREDRICK.getValue(), unsignedShortLe(packet, 0));
        assertEquals(0x23, Byte.toUnsignedInt(packet[2]));
        assertEquals(1, Byte.toUnsignedInt(packet[21]));
        assertEquals(29, packet.length);
        assertEquals(item.getItemId(), intLe(packet, 22));
        assertEquals(0, packet[packet.length - 3]);
        assertEquals(0, packet[packet.length - 2]);
        assertEquals(0, packet[packet.length - 1]);
    }

    @Test
    void shouldFailBeforeCreatingPacketWhenStorageLoadFails() throws SQLException {
        Character chr = characterWithMerchantMeso(0);
        when(chr.getId()).thenReturn(77);
        SQLException failure = new SQLException("merchant storage unavailable");

        try (var database = mockStatic(DatabaseConnection.class)) {
            database.when(DatabaseConnection::getConnection).thenThrow(failure);

            SQLException thrown = assertThrows(SQLException.class, () -> PacketCreator.getFredrick(chr));

            assertSame(failure, thrown);
        }
    }

    private static Character characterWithMerchantMeso(int meso) {
        Character chr = mock(Character.class);
        when(chr.getMerchantNetMeso()).thenReturn(meso);
        return chr;
    }

    private static int unsignedShortLe(byte[] bytes, int offset) {
        return Byte.toUnsignedInt(bytes[offset]) | Byte.toUnsignedInt(bytes[offset + 1]) << 8;
    }

    private static int intLe(byte[] bytes, int offset) {
        return Byte.toUnsignedInt(bytes[offset])
                | Byte.toUnsignedInt(bytes[offset + 1]) << 8
                | Byte.toUnsignedInt(bytes[offset + 2]) << 16
                | Byte.toUnsignedInt(bytes[offset + 3]) << 24;
    }
}
