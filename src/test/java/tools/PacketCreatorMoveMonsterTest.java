package tools;

import net.packet.Packet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import server.movement.AbsoluteLifeMovement;
import server.movement.LifeMovementFragment;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class PacketCreatorMoveMonsterTest {

    @ParameterizedTest
    @ValueSource(ints = {8, 9})
    void serializesHitActionWithNeutralAbsoluteMovement(int hitAction) {
        int standState = 4 | (hitAction & 1);
        AbsoluteLifeMovement movement = new AbsoluteLifeMovement(
                0, new Point(112, 202), 240, standState);
        movement.setPixelsPerSecond(new Point(0, 0));
        movement.setFh(42);

        Packet packet = PacketCreator.moveMonster(
                0x12345678,
                hitAction,
                new Point(100, 200),
                List.<LifeMovementFragment>of(movement));

        assertArrayEquals(new byte[]{
                (byte) 0xEF, 0x00,
                0x78, 0x56, 0x34, 0x12,
                0x00,
                0x00,
                (byte) hitAction,
                0x00,
                0x00,
                0x00, 0x00,
                0x64, 0x00,
                (byte) 0xC8, 0x00,
                0x01,
                0x00,
                0x70, 0x00,
                (byte) 0xCA, 0x00,
                0x00, 0x00,
                0x00, 0x00,
                0x2A, 0x00,
                (byte) standState,
                (byte) 0xF0, 0x00
        }, packet.getBytes());
    }
}
