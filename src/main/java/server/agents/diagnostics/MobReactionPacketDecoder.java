package server.agents.diagnostics;

import constants.skills.Bishop;
import constants.skills.Bowmaster;
import constants.skills.Brawler;
import constants.skills.ChiefBandit;
import constants.skills.Corsair;
import constants.skills.Evan;
import constants.skills.FPArchMage;
import constants.skills.Gunslinger;
import constants.skills.ILArchMage;
import constants.skills.Marksman;
import constants.skills.NightWalker;
import constants.skills.ThunderBreaker;
import constants.skills.WindArcher;
import net.opcodes.RecvOpcode;
import net.opcodes.SendOpcode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Defensive, human-readable decoding for the packet subset retained by a mob reaction capture.
 * All offsets are for decrypted packets that still include their two-byte little-endian opcode.
 */
public final class MobReactionPacketDecoder {
    private static final int OPCODE_BYTES = 2;
    private static final int INBOUND_MOVE_LIFE_MOVEMENT_OFFSET = 31;
    private static final int OUTBOUND_MOVE_MONSTER_MOVEMENT_OFFSET = 17;

    private MobReactionPacketDecoder() {
    }

    public static String recvOpcodeName(int opcode) {
        for (RecvOpcode value : RecvOpcode.values()) {
            if (value.getValue() == opcode) {
                return value.name();
            }
        }
        return "UNKNOWN_RECV";
    }

    public static String sendOpcodeName(int opcode) {
        for (SendOpcode value : SendOpcode.values()) {
            if (value.getValue() == opcode) {
                return value.name();
            }
        }
        return "UNKNOWN_SEND";
    }

    public static boolean isRelevantInbound(int opcode, byte[] fullPacket, int targetOid) {
        if (!has(fullPacket, 0, OPCODE_BYTES)) {
            return false;
        }

        if (opcode == RecvOpcode.CLOSE_RANGE_ATTACK.getValue()
                || opcode == RecvOpcode.RANGED_ATTACK.getValue()
                || opcode == RecvOpcode.MAGIC_ATTACK.getValue()
                || opcode == RecvOpcode.TOUCH_MONSTER_ATTACK.getValue()) {
            // The receiving attack formats are handler-specific. Retain them conservatively and
            // correlate them with the server's target-aware outgoing attack packet.
            return true;
        }
        if (opcode == RecvOpcode.MOVE_LIFE.getValue()
                || opcode == RecvOpcode.AUTO_AGGRO.getValue()) {
            return intEquals(fullPacket, OPCODE_BYTES, targetOid);
        }
        if (opcode == RecvOpcode.FIELD_DAMAGE_MOB.getValue()) {
            return intEquals(fullPacket, OPCODE_BYTES, targetOid);
        }
        if (opcode == RecvOpcode.MOB_DAMAGE_MOB_FRIENDLY.getValue()
                || opcode == RecvOpcode.MOB_DAMAGE_MOB.getValue()) {
            return intEquals(fullPacket, OPCODE_BYTES, targetOid)
                    || intEquals(fullPacket, OPCODE_BYTES + 8, targetOid);
        }
        return false;
    }

    public static boolean isRelevantOutbound(int opcode, byte[] fullPacket, int targetOid) {
        if (!has(fullPacket, 0, OPCODE_BYTES)) {
            return false;
        }

        if (isOutgoingAttack(opcode)) {
            // Keep all attack broadcasts on the captured map. This avoids discarding a useful
            // timing anchor when a malformed or special-skill body cannot be target-decoded.
            return true;
        }
        if (opcode == SendOpcode.SPAWN_MONSTER_CONTROL.getValue()) {
            return intEquals(fullPacket, OPCODE_BYTES + 1, targetOid);
        }
        if (opcode == SendOpcode.MOVE_MONSTER.getValue()
                || opcode == SendOpcode.MOVE_MONSTER_RESPONSE.getValue()
                || opcode == SendOpcode.DAMAGE_MONSTER.getValue()
                || opcode == SendOpcode.SPAWN_MONSTER.getValue()
                || opcode == SendOpcode.KILL_MONSTER.getValue()
                || opcode == SendOpcode.APPLY_MONSTER_STATUS.getValue()
                || opcode == SendOpcode.CANCEL_MONSTER_STATUS.getValue()
                || opcode == SendOpcode.RESET_MONSTER_ANIMATION.getValue()) {
            return intEquals(fullPacket, OPCODE_BYTES, targetOid);
        }
        return false;
    }

    public static List<String> decodeInbound(int opcode, byte[] fullPacket, int targetOid) {
        List<String> lines = validatePacket(opcode, fullPacket);
        if (!has(fullPacket, 0, OPCODE_BYTES)) {
            return lines;
        }

        if (opcode == RecvOpcode.MOVE_LIFE.getValue()) {
            decodeMoveLife(fullPacket, targetOid, lines);
        } else if (opcode == RecvOpcode.AUTO_AGGRO.getValue()) {
            decodeSingleOid("autoAggro", fullPacket, OPCODE_BYTES, targetOid, lines);
        } else if (opcode == RecvOpcode.FIELD_DAMAGE_MOB.getValue()) {
            decodeFieldDamageMob(fullPacket, targetOid, lines);
        } else if (opcode == RecvOpcode.MOB_DAMAGE_MOB_FRIENDLY.getValue()) {
            decodeMobDamageMob("mobDamageMobFriendly", fullPacket, targetOid, false, lines);
        } else if (opcode == RecvOpcode.MOB_DAMAGE_MOB.getValue()) {
            decodeMobDamageMob("mobDamageMob", fullPacket, targetOid, true, lines);
        } else if (opcode == RecvOpcode.CLOSE_RANGE_ATTACK.getValue()
                || opcode == RecvOpcode.RANGED_ATTACK.getValue()
                || opcode == RecvOpcode.MAGIC_ATTACK.getValue()
                || opcode == RecvOpcode.TOUCH_MONSTER_ATTACK.getValue()) {
            decodeClientAttack(opcode, fullPacket, targetOid, lines);
        } else {
            lines.add("no decoder for inbound opcode " + hexOpcode(opcode));
        }
        return lines;
    }

    public static List<String> decodeOutbound(int opcode, byte[] fullPacket, int targetOid) {
        List<String> lines = validatePacket(opcode, fullPacket);
        if (!has(fullPacket, 0, OPCODE_BYTES)) {
            return lines;
        }

        if (opcode == SendOpcode.MOVE_MONSTER.getValue()) {
            decodeMoveMonster(fullPacket, targetOid, lines);
        } else if (opcode == SendOpcode.MOVE_MONSTER_RESPONSE.getValue()) {
            decodeMoveMonsterResponse(fullPacket, targetOid, lines);
        } else if (opcode == SendOpcode.SPAWN_MONSTER_CONTROL.getValue()) {
            decodeMonsterControl(fullPacket, targetOid, lines);
        } else if (opcode == SendOpcode.DAMAGE_MONSTER.getValue()) {
            decodeDamageMonster(fullPacket, targetOid, lines);
        } else if (isOutgoingAttack(opcode)) {
            decodeAttack(fullPacket, targetOid, lines);
        } else if (opcode == SendOpcode.SPAWN_MONSTER.getValue()
                || opcode == SendOpcode.KILL_MONSTER.getValue()
                || opcode == SendOpcode.APPLY_MONSTER_STATUS.getValue()
                || opcode == SendOpcode.CANCEL_MONSTER_STATUS.getValue()
                || opcode == SendOpcode.RESET_MONSTER_ANIMATION.getValue()) {
            decodeSingleOid(sendOpcodeName(opcode), fullPacket, OPCODE_BYTES, targetOid, lines);
        } else {
            lines.add("no decoder for outbound opcode " + hexOpcode(opcode));
        }
        return lines;
    }

    private static void decodeClientAttack(int opcode, byte[] packet, int targetOid, List<String> lines) {
        Cursor cursor = new Cursor(packet, OPCODE_BYTES);
        Integer unknown = cursor.readUnsignedByte();
        Integer countByte = cursor.readUnsignedByte();
        Integer skillId = cursor.readInt();
        if (unknown == null || countByte == null || skillId == null) {
            lines.add("clientAttack truncated before common header; bytes=" + packet.length);
            return;
        }

        int targetCount = countByte >>> 4;
        int damageLinesPerTarget = countByte & 0x0F;
        String route = opcode == RecvOpcode.RANGED_ATTACK.getValue() ? "ranged"
                : opcode == RecvOpcode.MAGIC_ATTACK.getValue() ? "magic" : "close";
        StringBuilder header = new StringBuilder("clientAttackHeader route=").append(route)
                .append(" unknown=").append(unknown)
                .append(" countByte=").append(byteHex(countByte))
                .append(" targetCount=").append(targetCount)
                .append(" damageLinesPerTarget=").append(damageLinesPerTarget)
                .append(" skillId=").append(skillId);

        boolean charged = isChargedClientAttack(skillId);
        if (charged) {
            Integer charge = cursor.readInt();
            if (charge == null) {
                lines.add(header + " charged-layout truncated before charge; raw packet retained");
                return;
            }
            header.append(" charge=").append(charge);
        }
        lines.add(header.toString());

        byte[] opaque8 = cursor.readBytes(8);
        Integer display = cursor.readUnsignedByte();
        Integer direction = cursor.readUnsignedByte();
        Integer stance = cursor.readUnsignedByte();
        if (opaque8 == null || display == null || direction == null || stance == null) {
            lines.add("clientAttack truncated before display/direction/stance; remainingBytes="
                    + cursor.remaining());
            return;
        }

        if (skillId == ChiefBandit.MESO_EXPLOSION) {
            lines.add("clientAttackMotion opaque8=" + MovementPacketDecoder.hex(opaque8)
                    + " display=" + display + " direction=" + direction + " stance=" + stance);
            lines.add("clientAttackUnsupported special=meso-explosion; target body not decoded; "
                    + "raw packet retained");
            return;
        }

        boolean ranged = opcode == RecvOpcode.RANGED_ATTACK.getValue();
        Integer preSpeedByte = cursor.readUnsignedByte();
        Integer speed = cursor.readUnsignedByte();
        if (preSpeedByte == null || speed == null) {
            lines.add("clientAttack truncated before speed; remainingBytes=" + cursor.remaining());
            return;
        }

        StringBuilder motion = new StringBuilder("clientAttackMotion opaque8=")
                .append(MovementPacketDecoder.hex(opaque8))
                .append(" display=").append(display)
                .append(" direction=").append(direction)
                .append(" stance=").append(stance)
                .append(" preSpeedByte=").append(preSpeedByte)
                .append(" speed=").append(speed);
        if (ranged) {
            Integer postSpeedByte = cursor.readUnsignedByte();
            Integer rangedDirection = cursor.readUnsignedByte();
            byte[] opaque7 = cursor.readBytes(7);
            if (postSpeedByte == null || rangedDirection == null || opaque7 == null) {
                lines.add(motion + " ranged-layout truncated; remainingBytes=" + cursor.remaining());
                return;
            }
            motion.append(" postSpeedByte=").append(postSpeedByte)
                    .append(" rangedDirection=").append(rangedDirection)
                    .append(" postDirectionOpaque7=").append(MovementPacketDecoder.hex(opaque7));
        } else {
            byte[] opaque4 = cursor.readBytes(4);
            if (opaque4 == null) {
                lines.add(motion + " non-ranged layout truncated; remainingBytes=" + cursor.remaining());
                return;
            }
            motion.append(" postSpeedOpaque4=").append(MovementPacketDecoder.hex(opaque4));
        }
        lines.add(motion.toString());

        if (charged) {
            lines.add("clientAttackUnsupported special=charged-skill; target body not decoded; "
                    + "raw packet retained");
            return;
        }
        if (isContinuousRangedClientAttack(skillId)) {
            lines.add("clientAttackUnsupported special=continuous-or-piercing-ranged-skill; "
                    + "target body not decoded; raw packet retained");
            return;
        }

        for (int targetIndex = 0; targetIndex < targetCount; targetIndex++) {
            Integer oid = cursor.readInt();
            byte[] opaque4 = cursor.readBytes(4);
            Short curX = cursor.readShort();
            Short curY = cursor.readShort();
            Short nextX = cursor.readShort();
            Short nextY = cursor.readShort();
            Short delay = cursor.readShort();
            if (oid == null || opaque4 == null || curX == null || curY == null
                    || nextX == null || nextY == null || delay == null) {
                lines.add("clientAttackTarget[" + targetIndex
                        + "] truncated before damage lines; remainingBytes=" + cursor.remaining());
                return;
            }

            List<String> damageLines = new ArrayList<>(damageLinesPerTarget);
            for (int damageIndex = 0; damageIndex < damageLinesPerTarget; damageIndex++) {
                Integer wireDamage = cursor.readInt();
                if (wireDamage == null) {
                    lines.add("clientAttackTarget[" + targetIndex + "] truncated at damageLine["
                            + damageIndex + "]; remainingBytes=" + cursor.remaining());
                    return;
                }
                boolean bit31Set = (wireDamage & Integer.MIN_VALUE) != 0;
                damageLines.add((wireDamage & Integer.MAX_VALUE) + (bit31Set ? "(bit31Set)" : ""));
            }
            byte[] tail4 = cursor.readBytes(4);
            if (tail4 == null) {
                lines.add("clientAttackTarget[" + targetIndex
                        + "] truncated before 4-byte target tail; remainingBytes=" + cursor.remaining());
                return;
            }
            lines.add("clientAttackTarget[" + targetIndex + "] oid=" + oid
                    + " targetMatch=" + (oid == targetOid)
                    + " opaque4=" + MovementPacketDecoder.hex(opaque4)
                    + " curPos=(" + curX + ',' + curY + ')'
                    + " nextPos=(" + nextX + ',' + nextY + ')'
                    + " delay=" + delay + " damageLines=" + damageLines
                    + " tail4=" + MovementPacketDecoder.hex(tail4));
        }
        addTrailingBytes(packet, cursor.position(), lines);
    }

    private static void decodeFieldDamageMob(byte[] packet, int targetOid, List<String> lines) {
        if (!has(packet, 0, 10)) {
            lines.add("fieldDamageMob truncated: need 10 bytes, have " + packet.length);
            return;
        }
        int oid = readInt(packet, 2);
        lines.add("fieldDamageMob oid=" + oid + " targetMatch=" + (oid == targetOid)
                + " damage=" + readInt(packet, 6));
        addTrailingBytes(packet, 10, lines);
    }

    private static void decodeMoveLife(byte[] packet, int targetOid, List<String> lines) {
        if (!has(packet, 0, INBOUND_MOVE_LIFE_MOVEMENT_OFFSET)) {
            lines.add("moveLife truncated before movement section: need at least "
                    + INBOUND_MOVE_LIFE_MOVEMENT_OFFSET + " bytes, have " + packet.length);
            return;
        }

        int oid = readInt(packet, 2);
        short moveId = readShort(packet, 6);
        int pNibbles = unsignedByte(packet, 8);
        int rawUnsigned = unsignedByte(packet, 9);
        byte rawSigned = packet[9];
        int skillId = unsignedByte(packet, 10);
        int skillLevel = unsignedByte(packet, 11);
        short option = readShort(packet, 12);
        int postHeaderByte = unsignedByte(packet, 22);
        int postHeaderInt = readInt(packet, 23);
        short startX = readShort(packet, 27);
        short startY = readShort(packet, 29);

        lines.add("moveLife oid=" + oid + " targetMatch=" + (oid == targetOid)
                + " moveId=" + moveId + " pNibbles=" + pNibbles
                + " rawActivitySigned=" + rawSigned + " rawActivityUnsigned=" + rawUnsigned
                + " action=" + (rawUnsigned >>> 1) + " facingBit=" + (rawUnsigned & 1)
                + " skillId=" + skillId + " skillLevel=" + skillLevel + " option=" + option);
        lines.add("moveLife opaque8=" + MovementPacketDecoder.hex(Arrays.copyOfRange(packet, 14, 22))
                + " postHeaderByte=" + postHeaderByte + " postHeaderInt=" + postHeaderInt
                + " clientStart=(" + startX + ',' + startY + ")"
                + " currentServerStart=(" + startX + ',' + (startY - 2) + ")");
        addMovementLines(packet, INBOUND_MOVE_LIFE_MOVEMENT_OFFSET, lines);
    }

    private static void decodeMoveMonster(byte[] packet, int targetOid, List<String> lines) {
        if (!has(packet, 0, OUTBOUND_MOVE_MONSTER_MOVEMENT_OFFSET)) {
            lines.add("moveMonster truncated before movement section: need at least "
                    + OUTBOUND_MOVE_MONSTER_MOVEMENT_OFFSET + " bytes, have " + packet.length);
            return;
        }

        int oid = readInt(packet, 2);
        int reserved = unsignedByte(packet, 6);
        int skillPossible = unsignedByte(packet, 7);
        int rawUnsigned = unsignedByte(packet, 8);
        byte rawSigned = packet[8];
        int skillId = unsignedByte(packet, 9);
        int skillLevel = unsignedByte(packet, 10);
        short option = readShort(packet, 11);
        short startX = readShort(packet, 13);
        short startY = readShort(packet, 15);

        lines.add("moveMonster oid=" + oid + " targetMatch=" + (oid == targetOid)
                + " reserved=" + reserved + " skillPossible=" + skillPossible
                + " rawActivitySigned=" + rawSigned + " rawActivityUnsigned=" + rawUnsigned
                + " action=" + (rawUnsigned >>> 1) + " facingBit=" + (rawUnsigned & 1)
                + " skillId=" + skillId + " skillLevel=" + skillLevel + " option=" + option
                + " start=(" + startX + ',' + startY + ")");
        addMovementLines(packet, OUTBOUND_MOVE_MONSTER_MOVEMENT_OFFSET, lines);
    }

    private static void decodeMoveMonsterResponse(byte[] packet, int targetOid, List<String> lines) {
        if (!has(packet, 0, 13)) {
            lines.add("moveMonsterResponse truncated: need 13 bytes, have " + packet.length);
            return;
        }
        int oid = readInt(packet, 2);
        lines.add("moveMonsterResponse oid=" + oid + " targetMatch=" + (oid == targetOid)
                + " moveId=" + readShort(packet, 6)
                + " useSkills=" + unsignedByte(packet, 8)
                + " currentMp=" + readShort(packet, 9)
                + " skillId=" + unsignedByte(packet, 11)
                + " skillLevel=" + unsignedByte(packet, 12));
        addTrailingBytes(packet, 13, lines);
    }

    private static void decodeMonsterControl(byte[] packet, int targetOid, List<String> lines) {
        if (!has(packet, 0, 7)) {
            lines.add("spawnMonsterControl truncated: need 7 bytes, have " + packet.length);
            return;
        }
        int mode = unsignedByte(packet, 2);
        int oid = readInt(packet, 3);
        lines.add("spawnMonsterControl mode=" + mode + " oid=" + oid
                + " targetMatch=" + (oid == targetOid));
        if (mode == 0) {
            addTrailingBytes(packet, 7, lines);
        } else if (packet.length > 7) {
            lines.add("spawn/control bodyBytesAfterOid=" + (packet.length - 7)
                    + " (not decoded)");
        }
    }

    private static void decodeDamageMonster(byte[] packet, int targetOid, List<String> lines) {
        if (!has(packet, 0, 11)) {
            lines.add("damageMonster truncated before damage: need 11 bytes, have " + packet.length);
            return;
        }
        int oid = readInt(packet, 2);
        StringBuilder decoded = new StringBuilder("damageMonster oid=").append(oid)
                .append(" targetMatch=").append(oid == targetOid)
                .append(" directionByte=").append(unsignedByte(packet, 6))
                .append(" damage=").append(readInt(packet, 7));
        if (has(packet, 11, 4)) {
            decoded.append(" currentHp=").append(readInt(packet, 11));
        }
        if (has(packet, 15, 4)) {
            decoded.append(" maxHp=").append(readInt(packet, 15));
        }
        lines.add(decoded.toString());
        int decodedLength = packet.length >= 19 ? 19 : packet.length >= 15 ? 15 : 11;
        addTrailingBytes(packet, decodedLength, lines);
    }

    private static void decodeAttack(byte[] packet, int targetOid, List<String> lines) {
        Cursor cursor = new Cursor(packet, OPCODE_BYTES);
        Integer characterId = cursor.readInt();
        Integer countByte = cursor.readUnsignedByte();
        Integer firstMarker = cursor.readUnsignedByte();
        Integer skillLevel = cursor.readUnsignedByte();
        if (characterId == null || countByte == null || firstMarker == null || skillLevel == null) {
            lines.add("attack truncated before basic header; bytes=" + packet.length);
            return;
        }

        int targetCount = countByte >>> 4;
        int damageLinesPerTarget = countByte & 0x0F;
        Integer skillId = skillLevel > 0 ? cursor.readInt() : 0;
        Integer display = cursor.readUnsignedByte();
        Integer direction = cursor.readUnsignedByte();
        Integer stance = cursor.readUnsignedByte();
        Integer speed = cursor.readUnsignedByte();
        Integer secondMarker = cursor.readUnsignedByte();
        Integer projectile = cursor.readInt();
        if (skillId == null || display == null || direction == null || stance == null
                || speed == null || secondMarker == null || projectile == null) {
            lines.add("attack truncated in variable header; bytes=" + packet.length);
            return;
        }

        lines.add("attackHeader characterId=" + characterId
                + " targetCount=" + targetCount + " damageLinesPerTarget=" + damageLinesPerTarget
                + " marker1=" + byteHex(firstMarker) + " skillLevel=" + skillLevel
                + " skillId=" + skillId + " display=" + display + " direction=" + direction
                + " stance=" + stance + " speed=" + speed
                + " marker2=" + byteHex(secondMarker) + " projectile=" + projectile);

        for (int targetIndex = 0; targetIndex < targetCount; targetIndex++) {
            Integer oid = cursor.readInt();
            Integer marker = cursor.readUnsignedByte();
            if (oid == null || marker == null) {
                lines.add("attackTarget[" + targetIndex + "] truncated before target header; remainingBytes="
                        + cursor.remaining());
                return;
            }

            int damageLineCount = damageLinesPerTarget;
            if (skillId == ChiefBandit.MESO_EXPLOSION) {
                Integer perTargetLineCount = cursor.readUnsignedByte();
                if (perTargetLineCount == null) {
                    lines.add("attackTarget[" + targetIndex
                            + "] truncated before per-target damage-line count");
                    return;
                }
                damageLineCount = perTargetLineCount;
            }

            List<String> damageLines = new ArrayList<>(damageLineCount);
            for (int damageIndex = 0; damageIndex < damageLineCount; damageIndex++) {
                Integer encoded = cursor.readInt();
                if (encoded == null) {
                    lines.add("attackTarget[" + targetIndex + "] truncated at damageLine["
                            + damageIndex + "]; remainingBytes=" + cursor.remaining());
                    return;
                }
                boolean criticalBit = (encoded & Integer.MIN_VALUE) != 0;
                damageLines.add((encoded & Integer.MAX_VALUE) + (criticalBit ? "(criticalBit)" : ""));
            }
            lines.add("attackTarget[" + targetIndex + "] oid=" + oid
                    + " targetMatch=" + (oid == targetOid) + " marker=" + byteHex(marker)
                    + " damageLines=" + damageLines);
        }
        addTrailingBytes(packet, cursor.position(), lines);
    }

    private static void decodeMobDamageMob(String label,
                                           byte[] packet,
                                           int targetOid,
                                           boolean includesDamage,
                                           List<String> lines) {
        int required = includesDamage ? 19 : 14;
        if (!has(packet, 0, required)) {
            lines.add(label + " truncated: need " + required + " bytes, have " + packet.length);
            return;
        }
        int attackerOid = readInt(packet, 2);
        int damagedOid = readInt(packet, 10);
        StringBuilder decoded = new StringBuilder(label)
                .append(" attackerOid=").append(attackerOid)
                .append(" damagedOid=").append(damagedOid)
                .append(" targetInvolved=").append(attackerOid == targetOid || damagedOid == targetOid);
        if (includesDamage) {
            decoded.append(" attackTypeByte=").append(unsignedByte(packet, 14))
                    .append(" damage=").append(readInt(packet, 15));
        }
        lines.add(decoded.toString());
        addTrailingBytes(packet, required, lines);
    }

    private static void decodeSingleOid(String label,
                                        byte[] packet,
                                        int oidOffset,
                                        int targetOid,
                                        List<String> lines) {
        if (!has(packet, oidOffset, Integer.BYTES)) {
            lines.add(label + " truncated before oid: need " + (oidOffset + Integer.BYTES)
                    + " bytes, have " + packet.length);
            return;
        }
        int oid = readInt(packet, oidOffset);
        lines.add(label + " oid=" + oid + " targetMatch=" + (oid == targetOid));
        addTrailingBytes(packet, oidOffset + Integer.BYTES, lines);
    }

    private static void addMovementLines(byte[] packet, int offset, List<String> lines) {
        byte[] movement = Arrays.copyOfRange(packet, offset, packet.length);
        lines.add("movementBytes=" + movement.length
                + " (fields below use the current server MovementPacketDecoder)");
        for (String movementLine : MovementPacketDecoder.decode(movement)) {
            lines.add("movement(currentServerDecoder): " + movementLine);
        }
    }

    private static void addTrailingBytes(byte[] packet, int offset, List<String> lines) {
        if (packet.length > offset) {
            lines.add("trailingBytes="
                    + MovementPacketDecoder.hex(Arrays.copyOfRange(packet, offset, packet.length)));
        }
    }

    private static List<String> validatePacket(int opcode, byte[] packet) {
        List<String> lines = new ArrayList<>();
        if (packet == null) {
            lines.add("packet is null");
            return lines;
        }
        if (packet.length < OPCODE_BYTES) {
            lines.add("packet truncated before 2-byte opcode; bytes=" + packet.length);
            return lines;
        }
        int packetOpcode = readUnsignedShort(packet, 0);
        if (packetOpcode != (opcode & 0xFFFF)) {
            lines.add("opcode argument " + hexOpcode(opcode) + " differs from packet opcode "
                    + hexOpcode(packetOpcode) + "; decoding as argument opcode");
        }
        return lines;
    }

    private static boolean isOutgoingAttack(int opcode) {
        return opcode == SendOpcode.CLOSE_RANGE_ATTACK.getValue()
                || opcode == SendOpcode.RANGED_ATTACK.getValue()
                || opcode == SendOpcode.MAGIC_ATTACK.getValue();
    }

    private static boolean isChargedClientAttack(int skillId) {
        return skillId == Evan.ICE_BREATH
                || skillId == Evan.FIRE_BREATH
                || skillId == FPArchMage.BIG_BANG
                || skillId == ILArchMage.BIG_BANG
                || skillId == Bishop.BIG_BANG
                || skillId == Gunslinger.GRENADE
                || skillId == Brawler.CORKSCREW_BLOW
                || skillId == ThunderBreaker.CORKSCREW_BLOW
                || skillId == NightWalker.POISON_BOMB;
    }

    private static boolean isContinuousRangedClientAttack(int skillId) {
        return skillId == Bowmaster.HURRICANE
                || skillId == Marksman.PIERCING_ARROW
                || skillId == Corsair.RAPID_FIRE
                || skillId == WindArcher.HURRICANE;
    }

    private static boolean intEquals(byte[] data, int offset, int expected) {
        return has(data, offset, Integer.BYTES) && readInt(data, offset) == expected;
    }

    private static boolean has(byte[] data, int offset, int length) {
        return data != null && offset >= 0 && length >= 0 && offset <= data.length - length;
    }

    private static int unsignedByte(byte[] data, int offset) {
        return Byte.toUnsignedInt(data[offset]);
    }

    private static short readShort(byte[] data, int offset) {
        return (short) readUnsignedShort(data, offset);
    }

    private static int readUnsignedShort(byte[] data, int offset) {
        return unsignedByte(data, offset) | unsignedByte(data, offset + 1) << 8;
    }

    private static int readInt(byte[] data, int offset) {
        return unsignedByte(data, offset)
                | unsignedByte(data, offset + 1) << 8
                | unsignedByte(data, offset + 2) << 16
                | unsignedByte(data, offset + 3) << 24;
    }

    private static String byteHex(int value) {
        return String.format("0x%02X", value & 0xFF);
    }

    private static String hexOpcode(int opcode) {
        return String.format("0x%04X", opcode & 0xFFFF);
    }

    private static final class Cursor {
        private final byte[] data;
        private int position;

        private Cursor(byte[] data, int position) {
            this.data = data;
            this.position = position;
        }

        private int position() {
            return position;
        }

        private int remaining() {
            return Math.max(0, data.length - position);
        }

        private Integer readUnsignedByte() {
            if (!has(data, position, Byte.BYTES)) {
                return null;
            }
            return unsignedByte(data, position++);
        }

        private Integer readInt() {
            if (!has(data, position, Integer.BYTES)) {
                return null;
            }
            int value = MobReactionPacketDecoder.readInt(data, position);
            position += Integer.BYTES;
            return value;
        }

        private Short readShort() {
            if (!has(data, position, Short.BYTES)) {
                return null;
            }
            short value = MobReactionPacketDecoder.readShort(data, position);
            position += Short.BYTES;
            return value;
        }

        private byte[] readBytes(int length) {
            if (!has(data, position, length)) {
                return null;
            }
            byte[] value = Arrays.copyOfRange(data, position, position + length);
            position += length;
            return value;
        }
    }
}
