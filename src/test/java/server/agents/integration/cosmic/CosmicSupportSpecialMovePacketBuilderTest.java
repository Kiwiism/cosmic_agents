package server.agents.integration.cosmic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import client.Character;
import constants.skills.Cleric;
import constants.skills.Magician;
import java.awt.Point;
import org.junit.jupiter.api.Test;
import tools.HexTool;

class CosmicSupportSpecialMovePacketBuilderTest {
    @Test
    void shouldMatchRealMagicGuardSpecialMovePacketLayout() {
        Character agent = agentAt(new Point(100, 200), false);

        byte[] packet = CosmicSupportSpecialMovePacketBuilder.build(
                agent, Magician.MAGIC_GUARD, 20, 0x009195A5);

        assertArrayEquals(HexTool.toBytes("5B 00 A5 95 91 00 6A 88 1E 00 14 00 00"), packet);
    }

    @Test
    void shouldMatchRealBlessSpecialMovePacketLayout() {
        Character agent = agentAt(new Point(0x155D, 0x01C6), true);

        byte[] packet = CosmicSupportSpecialMovePacketBuilder.build(
                agent, Cleric.BLESS, 9, 0x00919AAF);

        assertArrayEquals(HexTool.toBytes("5B 00 AF 9A 91 00 4C 1C 23 00 09 5D 15 C6 01 80 00 00"), packet);
    }

    private static Character agentAt(Point position, boolean facingLeft) {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(position);
        when(agent.isFacingLeft()).thenReturn(facingLeft);
        return agent;
    }
}
