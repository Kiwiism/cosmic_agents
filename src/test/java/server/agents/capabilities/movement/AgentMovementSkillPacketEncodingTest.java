package server.agents.capabilities.movement;

import constants.game.CharacterStance;
import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentMovementSkillPacketEncodingTest {
    private static final AgentMovementPacketSnapshot SNAPSHOT = new AgentMovementPacketSnapshot(
            12,
            -34,
            CharacterStance.JUMP_RIGHT_STANCE);

    @Test
    void flashJumpUsesAbsoluteAnchorBeforeRelativeAction() {
        byte[] data = AgentMovementBroadcastService.buildFlashJumpMovementData(
                new Point(100, 200), SNAPSHOT, 321, 55, -66, 50);

        assertEquals(23, data.length);
        assertEquals(2, unsigned(data[0]));

        assertEquals(0, unsigned(data[1]));
        assertEquals(100, signedShort(data, 2));
        assertEquals(200, signedShort(data, 4));
        assertEquals(12, signedShort(data, 6));
        assertEquals(-34, signedShort(data, 8));
        assertEquals(321, signedShort(data, 10));
        assertEquals(CharacterStance.JUMP_RIGHT_STANCE, unsigned(data[12]));
        assertEquals(50, signedShort(data, 13));

        assertEquals(6, unsigned(data[15]));
        assertEquals(55, signedShort(data, 16));
        assertEquals(-66, signedShort(data, 18));
        assertEquals(CharacterStance.JUMP_RIGHT_STANCE, unsigned(data[20]));
        assertEquals(0, signedShort(data, 21));
    }

    @Test
    void teleportUsesClientLayoutAndImmediateLandingSettle() {
        byte[] data = AgentMovementBroadcastService.buildTeleportMovementData(
                new Point(100, 200), new Point(250, 210), SNAPSHOT, 77, 321, 50);

        assertEquals(35, data.length);
        assertEquals(3, unsigned(data[0]));

        assertEquals(4, unsigned(data[1]));
        assertEquals(100, signedShort(data, 2));
        assertEquals(200, signedShort(data, 4));
        assertEquals(77, signedShort(data, 6));
        assertEquals(CharacterStance.JUMP_RIGHT_STANCE, unsigned(data[8]));
        assertEquals(0, signedShort(data, 9));

        assertEquals(3, unsigned(data[11]));
        assertEquals(250, signedShort(data, 12));
        assertEquals(210, signedShort(data, 14));
        assertEquals(0, signedShort(data, 16));
        assertEquals(CharacterStance.JUMP_RIGHT_STANCE, unsigned(data[18]));
        assertEquals(0, signedShort(data, 19));

        assertEquals(0, unsigned(data[21]));
        assertEquals(250, signedShort(data, 22));
        assertEquals(210, signedShort(data, 24));
        assertEquals(12, signedShort(data, 26));
        assertEquals(-34, signedShort(data, 28));
        assertEquals(321, signedShort(data, 30));
        assertEquals(CharacterStance.JUMP_RIGHT_STANCE, unsigned(data[32]));
        assertEquals(50, signedShort(data, 33));
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }

    private static int signedShort(byte[] data, int offset) {
        return (short) (unsigned(data[offset]) | (unsigned(data[offset + 1]) << 8));
    }
}
