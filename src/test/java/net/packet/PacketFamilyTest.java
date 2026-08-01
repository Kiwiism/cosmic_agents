package net.packet;

import net.opcodes.RecvOpcode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacketFamilyTest {
    @Test
    void classifiesSecuritySensitivePacketFamilies() {
        assertEquals(PacketFamily.AUTH, PacketFamily.classify((short) RecvOpcode.LOGIN_PASSWORD.getValue()));
        assertEquals(PacketFamily.MOVEMENT, PacketFamily.classify((short) RecvOpcode.MOVE_PLAYER.getValue()));
        assertEquals(PacketFamily.COMBAT, PacketFamily.classify((short) RecvOpcode.CLOSE_RANGE_ATTACK.getValue()));
        assertEquals(PacketFamily.CHAT, PacketFamily.classify((short) RecvOpcode.GENERAL_CHAT.getValue()));
        assertEquals(PacketFamily.ECONOMY, PacketFamily.classify((short) RecvOpcode.PLAYER_INTERACTION.getValue()));
        assertEquals(PacketFamily.ECONOMY, PacketFamily.classify((short) RecvOpcode.DUEY_ACTION.getValue()));
    }
}
