package tools;

import client.Character;
import io.netty.buffer.Unpooled;
import net.packet.ByteBufInPacket;
import net.packet.InPacket;
import net.packet.Packet;
import net.server.channel.handlers.AbstractDealDamageHandler.AttackTarget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PacketCreatorAttackDamageStyleTest {
    private static final int CHARACTER_ID = 101;
    private static final int MONSTER_OBJECT_ID = 202;
    private static final int DAMAGE = 1234;

    @Test
    void ordinaryPlayerDamageRemainsPositiveOnBroadcast() {
        AttackTarget target = new AttackTarget((short) 0, List.of(DAMAGE));

        assertEquals(DAMAGE, encodedDamage(target));
    }

    @Test
    void explicitlyCriticalDamageSetsOnlyBitThirtyOne() {
        AttackTarget target = new AttackTarget((short) 0, List.of(DAMAGE), Set.of(0));

        assertEquals(DAMAGE | Integer.MIN_VALUE, encodedDamage(target));
    }

    private static int encodedDamage(AttackTarget target) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(CHARACTER_ID);
        Packet packet = PacketCreator.closeRangeAttack(
                character,
                0,
                0,
                0,
                0x11,
                Map.of(MONSTER_OBJECT_ID, target),
                4,
                0,
                0);

        InPacket input = new ByteBufInPacket(Unpooled.wrappedBuffer(packet.getBytes()));
        input.readShort(); // opcode
        assertEquals(CHARACTER_ID, input.readInt());
        input.skip(8); // counts, marker, skill level, display, direction, stance, speed, constant
        input.readInt(); // projectile
        assertEquals(MONSTER_OBJECT_ID, input.readInt());
        input.readByte(); // per-target marker
        return input.readInt();
    }
}
