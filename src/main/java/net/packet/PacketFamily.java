package net.packet;

import net.opcodes.RecvOpcode;

import java.util.Set;

public enum PacketFamily {
    AUTH,
    MOVEMENT,
    COMBAT,
    CHAT,
    ECONOMY,
    OTHER;

    private static final Set<Integer> MOVEMENT_OPCODES = Set.of(
            value(RecvOpcode.MOVE_PLAYER), value(RecvOpcode.MOVE_PET), value(RecvOpcode.MOVE_SUMMON),
            value(RecvOpcode.MOVE_DRAGON), value(RecvOpcode.MOVE_LIFE), value(RecvOpcode.AUTO_AGGRO));
    private static final Set<Integer> COMBAT_OPCODES = Set.of(
            value(RecvOpcode.CLOSE_RANGE_ATTACK), value(RecvOpcode.RANGED_ATTACK), value(RecvOpcode.MAGIC_ATTACK),
            value(RecvOpcode.TOUCH_MONSTER_ATTACK), value(RecvOpcode.TAKE_DAMAGE), value(RecvOpcode.SPECIAL_MOVE),
            value(RecvOpcode.SKILL_EFFECT), value(RecvOpcode.SUMMON_ATTACK), value(RecvOpcode.DAMAGE_SUMMON));
    private static final Set<Integer> CHAT_OPCODES = Set.of(
            value(RecvOpcode.GENERAL_CHAT), value(RecvOpcode.MULTI_CHAT), value(RecvOpcode.WHISPER),
            value(RecvOpcode.SPOUSE_CHAT), value(RecvOpcode.MESSENGER));
    private static final Set<Integer> ECONOMY_OPCODES = Set.of(
            value(RecvOpcode.NPC_SHOP), value(RecvOpcode.STORAGE), value(RecvOpcode.HIRED_MERCHANT_REQUEST),
            value(RecvOpcode.FREDRICK_ACTION), value(RecvOpcode.DUEY_ACTION), value(RecvOpcode.OWL_ACTION),
            value(RecvOpcode.OWL_WARP), value(RecvOpcode.ITEM_MOVE), value(RecvOpcode.MESO_DROP),
            value(RecvOpcode.PLAYER_INTERACTION), value(RecvOpcode.ENTER_MTS), value(RecvOpcode.MTS_OPERATION),
            value(RecvOpcode.CASHSHOP_OPERATION), value(RecvOpcode.ITEM_PICKUP));

    public static PacketFamily classify(short opcode) {
        int value = Short.toUnsignedInt(opcode);
        if (value <= RecvOpcode.VIEW_ALL_WITH_PIC.getValue()) {
            return AUTH;
        }
        if (MOVEMENT_OPCODES.contains(value)) {
            return MOVEMENT;
        }
        if (COMBAT_OPCODES.contains(value)) {
            return COMBAT;
        }
        if (CHAT_OPCODES.contains(value)) {
            return CHAT;
        }
        if (ECONOMY_OPCODES.contains(value)) {
            return ECONOMY;
        }
        return OTHER;
    }

    private static int value(RecvOpcode opcode) {
        return opcode.getValue();
    }
}
