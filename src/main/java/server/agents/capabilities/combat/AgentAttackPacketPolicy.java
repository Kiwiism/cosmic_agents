package server.agents.capabilities.combat;

/** Bounds attack counts to the two four-bit fields used by v83 attack packets. */
public final class AgentAttackPacketPolicy {
    public static final int MAX_FIELD_COUNT = 15;

    private AgentAttackPacketPolicy() {
    }

    public static int targetCount(int requested) {
        return Math.clamp(requested, 0, MAX_FIELD_COUNT);
    }

    public static int damageLineCount(int requested) {
        return Math.clamp(requested, 0, MAX_FIELD_COUNT);
    }

    public static int packCounts(int targets, int damageLines) {
        return (targetCount(targets) << 4) | damageLineCount(damageLines);
    }
}
