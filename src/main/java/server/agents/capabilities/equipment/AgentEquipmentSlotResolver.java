package server.agents.capabilities.equipment;

import client.inventory.Equip;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves chat-facing equipment slot aliases to equipped inventory positions.
 */
public final class AgentEquipmentSlotResolver {
    private static final short[] RING_SLOTS = {-12, -13, -15, -16};

    private AgentEquipmentSlotResolver() {
    }

    /** Returns the equipped slot(s) that match the given slot name from chat. Empty array = unknown. */
    public static short[] slotsFromName(String name) {
        if (name == null) {
            return new short[0];
        }
        return switch (name.trim().toLowerCase().replaceAll("\\s+", "")) {
            case "hat", "helm", "helmet" -> new short[]{-1};
            case "face", "faceacc", "faceaccessory" -> new short[]{-2};
            case "eye", "eyeacc", "eyeaccessory", "eyepiece" -> new short[]{-3};
            case "ear", "earring", "earrings" -> new short[]{-4};
            case "top", "shirt", "overall" -> new short[]{-5};
            case "bottom", "pant", "pants" -> new short[]{-6};
            case "shoe", "shoes", "boot", "boots" -> new short[]{-7};
            case "glove", "gloves" -> new short[]{-8};
            case "cape", "capes" -> new short[]{-9};
            case "shield", "shields", "offhand" -> new short[]{-10};
            case "weapon", "weapons", "wep" -> new short[]{-11};
            case "ring" -> RING_SLOTS.clone();
            case "ring1" -> new short[]{-12};
            case "ring2" -> new short[]{-13};
            case "ring3" -> new short[]{-15};
            case "ring4" -> new short[]{-16};
            case "petwear" -> new short[]{-14};
            case "pendant" -> new short[]{-17};
            case "medal" -> new short[]{-49};
            case "belt" -> new short[]{-50};
            default -> new short[0];
        };
    }

    public static List<Short> buildDpSlots(Map<Short, List<Equip>> bySlot,
                                           Map<Short, Equip> currentBySlot) {
        Set<Short> set = new HashSet<>();
        for (Short slot : bySlot.keySet()) {
            if (slot != (short) -11 && !isRingSlot(slot)) {
                set.add(slot);
            }
        }
        for (Short slot : currentBySlot.keySet()) {
            if (slot != (short) -11 && !isRingSlot(slot)) {
                set.add(slot);
            }
        }
        boolean hasRings = !bySlot.getOrDefault((short) -12, List.of()).isEmpty();
        if (!hasRings) {
            for (Short slot : currentBySlot.keySet()) {
                if (isRingSlot(slot)) {
                    hasRings = true;
                    break;
                }
            }
        }
        if (hasRings) {
            for (short ringSlot : RING_SLOTS) {
                set.add(ringSlot);
            }
        }
        List<Short> result = new ArrayList<>(set);
        result.sort((left, right) -> Short.compare(right, left));
        return result;
    }

    public static boolean isRingSlot(short slot) {
        for (short ringSlot : RING_SLOTS) {
            if (slot == ringSlot) {
                return true;
            }
        }
        return false;
    }

    public static String slotLabel(short slot) {
        return switch (slot) {
            case -11 -> "weapon";
            case -10 -> "shield";
            case -9 -> "cape";
            case -8 -> "glove";
            case -7 -> "shoes";
            case -6 -> "pants";
            case -5 -> "top";
            case -4 -> "earring";
            case -3 -> "face";
            case -2 -> "eye";
            case -1 -> "hat";
            case -12, -13, -15, -16 -> "ring";
            case -17 -> "pendant";
            case -18 -> "tamed mob";
            case -19 -> "saddle";
            case -20 -> "medal";
            case -21 -> "belt";
            default -> "slot " + slot;
        };
    }
}
