package server.bots.combat;

public final class BotAttackTiming {
    private BotAttackTiming() {
    }

    public static int normalizeAttackSpeed(int attackSpeed) {
        if (attackSpeed <= 0) {
            return 4;
        }
        return attackSpeed;
    }

    public static float toAttackSpeedFactor(int attackSpeed) {
        return 1.7f - (normalizeAttackSpeed(attackSpeed) / 10f);
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
