package server.agents.capabilities.equipment;

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
}
