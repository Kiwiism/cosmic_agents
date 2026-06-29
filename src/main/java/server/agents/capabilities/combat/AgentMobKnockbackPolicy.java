package server.agents.capabilities.combat;

public final class AgentMobKnockbackPolicy {
    private AgentMobKnockbackPolicy() {
    }

    public static boolean shouldApplyMobKnockback(boolean climbing, int currentHp,
                                                  Integer stancePercent, float randomRoll) {
        if (climbing || currentHp <= 0) {
            return false;
        }
        if (stancePercent == null || stancePercent <= 0) {
            return true;
        }

        float stanceChance = Math.max(0f, Math.min(1f, stancePercent / 100f));
        return randomRoll > stanceChance;
    }
}
