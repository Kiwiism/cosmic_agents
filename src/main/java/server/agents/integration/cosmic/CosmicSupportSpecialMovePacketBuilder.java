package server.agents.integration.cosmic;

import client.Character;
import java.awt.Point;
import net.opcodes.RecvOpcode;
import net.packet.ByteBufOutPacket;
import server.agents.capabilities.combat.AgentCombatSkillClassifier;

public final class CosmicSupportSpecialMovePacketBuilder {
    private CosmicSupportSpecialMovePacketBuilder() {
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

    /** Monster Magnet: count, then (object id, success byte) per monster, then the caster's direction. */
    public static byte[] buildMonsterMagnet(Character agent, int skillId, int skillLevel, int packetTimestamp,
                                            java.util.List<Integer> monsterOids) {
        ByteBufOutPacket packet = new ByteBufOutPacket();
        packet.writeShort(RecvOpcode.SPECIAL_MOVE.getValue());
        packet.writeInt(packetTimestamp);
        packet.writeInt(skillId);
        packet.writeByte(skillLevel);
        packet.writeInt(monsterOids.size());
        for (int oid : monsterOids) {
            packet.writeInt(oid);
            packet.writeByte(1);
        }
        packet.writeByte(agent.isFacingLeft() ? 1 : 0);
        return packet.getBytes();
    }

    /**
     * Summons: SpecialMoveHandler reads a position only when exactly five bytes remain after the
     * skill level (x, y, and one trailing byte), and StatEffect spawns the summon at that point.
     */
    public static byte[] buildWithPosition(Character agent, int skillId, int skillLevel, int packetTimestamp) {
        ByteBufOutPacket packet = new ByteBufOutPacket();
        packet.writeShort(RecvOpcode.SPECIAL_MOVE.getValue());
        packet.writeInt(packetTimestamp);
        packet.writeInt(skillId);
        packet.writeByte(skillLevel);
        Point position = agent.getPosition();
        packet.writePos(position != null ? position : new Point(0, 0));
        packet.writeByte(0);
        return packet.getBytes();
    }
}
