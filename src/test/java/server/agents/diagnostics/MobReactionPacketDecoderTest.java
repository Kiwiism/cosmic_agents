package server.agents.diagnostics;

import net.opcodes.RecvOpcode;
import net.opcodes.SendOpcode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobReactionPacketDecoderTest {
    @Test
    void namesOpcodesByDirection() {
        assertEquals("MOVE_LIFE", MobReactionPacketDecoder.recvOpcodeName(0xBC));
        assertEquals("MAGIC_ATTACK", MobReactionPacketDecoder.sendOpcodeName(0xBC));
        assertEquals("UNKNOWN_RECV", MobReactionPacketDecoder.recvOpcodeName(0x7FFF));
        assertEquals("UNKNOWN_SEND", MobReactionPacketDecoder.sendOpcodeName(0x7FFF));
    }

    @Test
    void filtersMobSpecificPacketsAndRetainsAttackTimingAnchors() {
        int targetOid = 0x12345678;
        byte[] moveLife = packet(RecvOpcode.MOVE_LIFE.getValue(),
                0x78, 0x56, 0x34, 0x12);
        byte[] otherMoveLife = packet(RecvOpcode.MOVE_LIFE.getValue(),
                0x04, 0x03, 0x02, 0x01);
        byte[] control = packet(SendOpcode.SPAWN_MONSTER_CONTROL.getValue(),
                2, 0x78, 0x56, 0x34, 0x12);

        assertTrue(MobReactionPacketDecoder.isRelevantInbound(
                RecvOpcode.MOVE_LIFE.getValue(), moveLife, targetOid));
        assertFalse(MobReactionPacketDecoder.isRelevantInbound(
                RecvOpcode.MOVE_LIFE.getValue(), otherMoveLife, targetOid));
        assertTrue(MobReactionPacketDecoder.isRelevantInbound(
                RecvOpcode.CLOSE_RANGE_ATTACK.getValue(), packet(0x2C), targetOid));
        assertTrue(MobReactionPacketDecoder.isRelevantInbound(
                RecvOpcode.FIELD_DAMAGE_MOB.getValue(),
                packet(RecvOpcode.FIELD_DAMAGE_MOB.getValue(), 0x78, 0x56, 0x34, 0x12),
                targetOid));
        assertTrue(MobReactionPacketDecoder.isRelevantOutbound(
                SendOpcode.SPAWN_MONSTER_CONTROL.getValue(), control, targetOid));
        assertTrue(MobReactionPacketDecoder.isRelevantOutbound(
                SendOpcode.RANGED_ATTACK.getValue(), packet(0xBB), targetOid));
        assertFalse(MobReactionPacketDecoder.isRelevantOutbound(0x123, packet(0x123), targetOid));
        assertFalse(MobReactionPacketDecoder.isRelevantInbound(
                RecvOpcode.MOVE_LIFE.getValue(), new byte[]{(byte) 0xBC}, targetOid));
    }

    @Test
    void decodesInboundMoveLifeHeaderAndMovementAtByte31() {
        byte[] packet = packet(RecvOpcode.MOVE_LIFE.getValue(),
                0x78, 0x56, 0x34, 0x12,
                0xFE, 0xFF,
                0xA5,
                0x07,
                100,
                2,
                0xD4, 0xFE,
                1, 2, 3, 4, 5, 6, 7, 8,
                0x9A,
                4, 3, 2, 1,
                0x9C, 0xFF,
                0xC8, 0,
                1,
                1, 0xFC, 0xFF, 9, 0, 15, 33, 0);

        List<String> lines = MobReactionPacketDecoder.decodeInbound(
                RecvOpcode.MOVE_LIFE.getValue(), packet, 0x12345678);

        assertEquals("moveLife oid=305419896 targetMatch=true moveId=-2 pNibbles=165 "
                + "rawActivitySigned=7 rawActivityUnsigned=7 action=3 facingBit=1 skillId=100 "
                + "skillLevel=2 option=-300", lines.get(0));
        assertEquals("moveLife opaque8=01 02 03 04 05 06 07 08 postHeaderByte=154 "
                + "postHeaderInt=16909060 clientStart=(-100,200) currentServerStart=(-100,198)",
                lines.get(1));
        assertEquals("movementBytes=9 (fields below use the current server MovementPacketDecoder)",
                lines.get(2));
        assertEquals("movement(currentServerDecoder): fragmentCount=1", lines.get(3));
        assertEquals("movement(currentServerDecoder): fragment[0] cmd=1 relative x=-4 y=9 "
                + "stance=15(ladder-left) duration=33", lines.get(4));
    }

    @Test
    void decodesOutboundMoveMonsterAtByte17() {
        byte[] packet = packet(SendOpcode.MOVE_MONSTER.getValue(),
                0x78, 0x56, 0x34, 0x12,
                0,
                1,
                6,
                10,
                2,
                0x2C, 1,
                0xF6, 0xFF,
                40, 0,
                1, 10, 5);

        List<String> lines = MobReactionPacketDecoder.decodeOutbound(
                SendOpcode.MOVE_MONSTER.getValue(), packet, 0x12345678);

        assertEquals("moveMonster oid=305419896 targetMatch=true reserved=0 skillPossible=1 "
                + "rawActivitySigned=6 rawActivityUnsigned=6 action=3 facingBit=0 skillId=10 "
                + "skillLevel=2 option=300 start=(-10,40)", lines.get(0));
        assertEquals("movementBytes=3 (fields below use the current server MovementPacketDecoder)",
                lines.get(1));
        assertEquals("movement(currentServerDecoder): fragmentCount=1", lines.get(2));
        assertEquals("movement(currentServerDecoder): fragment[0] cmd=10 changeEquip value=5",
                lines.get(3));
    }

    @Test
    void decodesControllerResponseAndDamagePackets() {
        byte[] control = packet(SendOpcode.SPAWN_MONSTER_CONTROL.getValue(),
                0, 0x78, 0x56, 0x34, 0x12);
        byte[] response = packet(SendOpcode.MOVE_MONSTER_RESPONSE.getValue(),
                0x78, 0x56, 0x34, 0x12,
                9, 0,
                1,
                0xD2, 4,
                100,
                3);
        byte[] damage = packet(SendOpcode.DAMAGE_MONSTER.getValue(),
                0x78, 0x56, 0x34, 0x12,
                1,
                0xE8, 3, 0, 0,
                0x10, 0x27, 0, 0,
                0x20, 0x4E, 0, 0);

        assertEquals(List.of("spawnMonsterControl mode=0 oid=305419896 targetMatch=true"),
                MobReactionPacketDecoder.decodeOutbound(
                        SendOpcode.SPAWN_MONSTER_CONTROL.getValue(), control, 0x12345678));
        assertEquals(List.of("moveMonsterResponse oid=305419896 targetMatch=true moveId=9 "
                        + "useSkills=1 currentMp=1234 skillId=100 skillLevel=3"),
                MobReactionPacketDecoder.decodeOutbound(
                        SendOpcode.MOVE_MONSTER_RESPONSE.getValue(), response, 0x12345678));
        assertEquals(List.of("damageMonster oid=305419896 targetMatch=true directionByte=1 "
                        + "damage=1000 currentHp=10000 maxHp=20000"),
                MobReactionPacketDecoder.decodeOutbound(
                        SendOpcode.DAMAGE_MONSTER.getValue(), damage, 0x12345678));
    }

    @Test
    void decodesOutgoingAttackHeaderAndTargetsFromAddAttackBodyLayout() {
        byte[] packet = packet(SendOpcode.CLOSE_RANGE_ATTACK.getValue(),
                123, 0, 0, 0,
                0x12,
                0x5B,
                1,
                0xAD, 0xCA, 0x2D, 0,
                2,
                3,
                0x80,
                4,
                0x0A,
                0, 0, 0, 0,
                0x78, 0x56, 0x34, 0x12,
                0,
                100, 0, 0, 0,
                0xC8, 0, 0, 0x80);

        List<String> lines = MobReactionPacketDecoder.decodeOutbound(
                SendOpcode.CLOSE_RANGE_ATTACK.getValue(), packet, 0x12345678);

        assertEquals("attackHeader characterId=123 targetCount=1 damageLinesPerTarget=2 "
                + "marker1=0x5B skillLevel=1 skillId=3001005 display=2 direction=3 stance=128 "
                + "speed=4 marker2=0x0A projectile=0", lines.get(0));
        assertEquals("attackTarget[0] oid=305419896 targetMatch=true marker=0x00 "
                + "damageLines=[100, 200(criticalBit)]", lines.get(1));
    }

    @Test
    void decodesOrdinaryInboundCloseAttackTargetPositionsAndDelay() {
        byte[] packet = packet(RecvOpcode.CLOSE_RANGE_ATTACK.getValue(),
                0x11,
                0x12,
                0, 0, 0, 0,
                1, 2, 3, 4, 5, 6, 7, 8,
                2,
                3,
                0x80,
                4,
                5,
                9, 10, 11, 12,
                0x78, 0x56, 0x34, 0x12,
                13, 14, 15, 16,
                0xF6, 0xFF,
                20, 0,
                0xFB, 0xFF,
                25, 0,
                0x2C, 1,
                100, 0, 0, 0,
                0xC8, 0, 0, 0x80,
                0xAA, 0xBB, 0xCC, 0xDD);

        List<String> lines = MobReactionPacketDecoder.decodeInbound(
                RecvOpcode.CLOSE_RANGE_ATTACK.getValue(), packet, 0x12345678);

        assertEquals("clientAttackHeader route=close unknown=17 countByte=0x12 targetCount=1 "
                + "damageLinesPerTarget=2 skillId=0", lines.get(0));
        assertEquals("clientAttackMotion opaque8=01 02 03 04 05 06 07 08 display=2 direction=3 "
                + "stance=128 preSpeedByte=4 speed=5 postSpeedOpaque4=09 0A 0B 0C", lines.get(1));
        assertEquals("clientAttackTarget[0] oid=305419896 targetMatch=true "
                + "opaque4=0D 0E 0F 10 curPos=(-10,20) nextPos=(-5,25) delay=300 "
                + "damageLines=[100, 200(bit31Set)] tail4=AA BB CC DD", lines.get(2));
    }

    @Test
    void opcodeMismatchWarnsAndContinuesDecoding() {
        byte[] autoAggro = packet(RecvOpcode.AUTO_AGGRO.getValue(), 4, 3, 2, 1);

        List<String> lines = MobReactionPacketDecoder.decodeInbound(
                RecvOpcode.FIELD_DAMAGE_MOB.getValue(), autoAggro, 0x01020304);

        assertTrue(lines.get(0).contains("differs from packet opcode"));
        assertTrue(lines.get(1).contains("fieldDamageMob truncated"));
    }

    @Test
    void nullAndTruncatedPacketsNeverThrow() {
        List<String> nullLines = assertDoesNotThrow(() -> MobReactionPacketDecoder.decodeInbound(
                RecvOpcode.MOVE_LIFE.getValue(), null, 1));
        List<String> truncatedLines = assertDoesNotThrow(() -> MobReactionPacketDecoder.decodeOutbound(
                SendOpcode.MOVE_MONSTER.getValue(), packet(SendOpcode.MOVE_MONSTER.getValue(), 1), 1));
        List<String> truncatedAttack = assertDoesNotThrow(() -> MobReactionPacketDecoder.decodeOutbound(
                SendOpcode.CLOSE_RANGE_ATTACK.getValue(), packet(SendOpcode.CLOSE_RANGE_ATTACK.getValue(), 1), 1));

        assertEquals(List.of("packet is null"), nullLines);
        assertTrue(truncatedLines.get(0).contains("truncated before movement section"));
        assertTrue(truncatedAttack.get(0).contains("truncated before basic header"));
    }

    private static byte[] packet(int opcode, int... body) {
        byte[] packet = new byte[2 + body.length];
        packet[0] = (byte) opcode;
        packet[1] = (byte) (opcode >>> 8);
        for (int index = 0; index < body.length; index++) {
            packet[index + 2] = (byte) body[index];
        }
        return packet;
    }
}
