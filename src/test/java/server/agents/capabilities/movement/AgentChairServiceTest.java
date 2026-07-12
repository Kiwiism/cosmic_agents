package server.agents.capabilities.movement;

import constants.game.CharacterStance;
import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class AgentChairServiceTest {
    @Test
    void buildsSoloMaplingCompatibleRightFacingChairMovement() {
        byte[] movement = AgentChairService.chairMovementData(
                new Point(0x1234, -2), CharacterStance.SIT_RIGHT_STANCE);

        assertArrayEquals(new byte[]{
                1, 11,
                0x34, 0x12,
                (byte) 0xFE, (byte) 0xFF,
                0, 0,
                20,
                0, 0
        }, movement);
    }

    @Test
    void buildsLeftFacingChairMovement() {
        byte[] movement = AgentChairService.chairMovementData(
                new Point(0, 0), CharacterStance.SIT_LEFT_STANCE);

        assertArrayEquals(new byte[]{1, 11, 0, 0, 0, 0, 0, 0, 21, 0, 0}, movement);
    }
}
