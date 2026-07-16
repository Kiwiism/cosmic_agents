package tools;

import client.Character;
import client.Client;
import client.Job;
import client.inventory.InventoryType;
import constants.skills.Bishop;
import net.opcodes.SendOpcode;
import net.packet.Packet;
import net.server.channel.handlers.AbstractDealDamageHandler.AttackTarget;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PacketCreatorVisualProxyTest {
    @Test
    void visualProxyUsesItsSyntheticIdWithoutSerializingIdentityLinkedState() {
        Character appearance = mock(Character.class);
        Client target = mock(Client.class);
        when(appearance.getLevel()).thenReturn(95);
        PacketCreator.VisualProxyAppearance angel = new PacketCreator.VisualProxyAppearance(
                "Seraph", 1, 0, 21104, 31153,
                Map.of(1, 1002333, 5, 1051190, 11, 1372001),
                1702185, 4);

        Packet packet = PacketCreator.spawnPlayerVisualProxy(
                target, appearance, 900_000_000, new Point(-30_000, 123),
                Job.BISHOP.getId(), angel);

        assertEquals(SendOpcode.SPAWN_PLAYER.getValue(), opcode(packet));
        assertEquals(900_000_000, characterId(packet));
        assertTrue(containsInt(packet, 21104));
        assertTrue(containsInt(packet, 31153));
        assertTrue(containsInt(packet, 1002333));
        assertTrue(containsInt(packet, 1702185));
        verify(appearance, never()).getClient();
        verify(appearance, never()).getName();
        verify(appearance, never()).getGender();
        verify(appearance, never()).getSkinColor();
        verify(appearance, never()).getFace();
        verify(appearance, never()).getHair();
        verify(appearance, never()).getStance();
        verify(appearance, never()).getInventory(InventoryType.EQUIPPED);
        verify(appearance, never()).getPets();
        verify(appearance, never()).getMount();
        verify(appearance, never()).getPlayerShop();
        verify(appearance, never()).getMiniGame();
        verify(appearance, never()).getChalkboard();
        verify(appearance, never()).getReceivedNewYearRecords();
        verify(appearance, never()).getInventory(InventoryType.CASH);
    }

    @Test
    void ordinaryMagicAttackRemainsByteIdenticalToItsCharacterIdDelegate() {
        Character caster = mock(Character.class);
        when(caster.getId()).thenReturn(77);
        Map<Integer, AttackTarget> targets = Map.of(
                101, new AttackTarget((short) 0, List.of(456)));

        Packet ordinary = PacketCreator.magicAttack(
                caster, Bishop.GENESIS, 5, 0, 0x11,
                targets, -1, 4, 69, 0);
        Packet delegated = PacketCreator.magicAttackFromVisualProxy(
                77, Bishop.GENESIS, 5, 0, 0x11,
                targets, -1, 4, 69, 0);

        assertArrayEquals(ordinary.getBytes(), delegated.getBytes());
    }

    private static int opcode(Packet packet) {
        return Short.toUnsignedInt(ByteBuffer.wrap(packet.getBytes(), 0, Short.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getShort());
    }

    private static int characterId(Packet packet) {
        return ByteBuffer.wrap(packet.getBytes(), Short.BYTES, Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();
    }

    private static boolean containsInt(Packet packet, int value) {
        byte[] needle = ByteBuffer.allocate(Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value)
                .array();
        byte[] bytes = packet.getBytes();
        outer:
        for (int i = 0; i <= bytes.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (bytes[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
