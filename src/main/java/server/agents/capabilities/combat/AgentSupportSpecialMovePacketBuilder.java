package server.agents.capabilities.combat;

import client.Character;
import java.awt.Point;
import net.opcodes.RecvOpcode;
import net.packet.ByteBufOutPacket;

public final class AgentSupportSpecialMovePacketBuilder {
    private AgentSupportSpecialMovePacketBuilder() {
    }

    public static byte[] build(Character agent, int skillId, int skillLevel, int packetTimestamp) {
        ByteBufOutPacket packet = new ByteBufOutPacket();
        packet.writeShort(RecvOpcode.SPECIAL_MOVE.getValue());
        packet.writeInt(packetTimestamp);
        packet.writeInt(skillId);
        packet.writeByte(skillLevel);
        if (AgentCombatSkillClassifier.isPartySupportSkill(skillId)) {
            Point position = agent.getPosition();
            packet.writePos(position != null ? position : new Point(0, 0));
            packet.writeByte(agent.isFacingLeft() ? 0x80 : 0x00);
            packet.writeShort(0);
        } else {
            packet.writeShort(0);
        }
        return packet.getBytes();
    }
}
