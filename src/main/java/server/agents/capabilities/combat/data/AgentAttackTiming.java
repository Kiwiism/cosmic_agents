package server.agents.capabilities.combat.data;

public final class AgentAttackTiming {
    private static final float ATTACK_SPEED_BASE_FACTOR = config.AgentTuning.floatValue("server.agents.capabilities.combat.data.AgentAttackTiming.ATTACK_SPEED_BASE_FACTOR");
    private static final float ATTACK_SPEED_TIER_FACTOR = config.AgentTuning.floatValue("server.agents.capabilities.combat.data.AgentAttackTiming.ATTACK_SPEED_TIER_FACTOR");

    private AgentAttackTiming() {
    }

    public static int normalizeAttackSpeed(int attackSpeed) {
        if (attackSpeed <= 0) {
            return 4;
        }
        return attackSpeed;
    }

    public static float toAttackSpeedFactor(int attackSpeed) {
        return ATTACK_SPEED_BASE_FACTOR - (normalizeAttackSpeed(attackSpeed) * ATTACK_SPEED_TIER_FACTOR);
    }

    public static int adjustDelayMillis(int rawDelayMs, int attackSpeed) {
        if (rawDelayMs <= 0) {
            return 0;
        }

        float speedFactor = toAttackSpeedFactor(attackSpeed);
        if (speedFactor <= 0f) {
            return rawDelayMs;
        }

        return Math.max(1, Math.round(rawDelayMs / speedFactor));
    }
}
