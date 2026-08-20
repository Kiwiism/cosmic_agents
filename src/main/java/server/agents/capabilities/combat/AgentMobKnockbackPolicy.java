package server.agents.capabilities.combat;

import java.awt.Point;

public final class AgentMobKnockbackPolicy {
    public record MobHitKnockback(int direction, int airVelX) {
    }

    private AgentMobKnockbackPolicy() {
    }

    public static boolean shouldApplyMobKnockback(boolean climbing, int currentHp,
                                                  Integer stancePercent, float randomRoll) {
        return shouldApplyMobKnockback(climbing, currentHp, stancePercent, 0, randomRoll);
    }

    public static boolean shouldApplyMobKnockback(boolean climbing, int currentHp,
                                                  Integer stancePercent,
                                                  int additionalResistancePercent,
                                                  float randomRoll) {
        if (climbing || currentHp <= 0) {
            return false;
        }
        float stanceChance = stancePercent == null
                ? 0f : Math.max(0f, Math.min(1f, stancePercent / 100f));
        float additionalChance = Math.max(0f, Math.min(1f, additionalResistancePercent / 100f));
        float combinedResistance = 1f - (1f - stanceChance) * (1f - additionalChance);
        if (combinedResistance <= 0f) return true;
        if (combinedResistance >= 1f) return false;
        return randomRoll > combinedResistance;
    }

    public static MobHitKnockback resolveMobHitKnockback(Point agentPosition,
                                                         Point attackOrigin,
                                                         float knockbackHspeed,
                                                         int tickMs) {
        boolean attackFromRight = attackOrigin.x > agentPosition.x;
        int direction = attackFromRight ? 0 : 1;
        int airVelX = Math.round((attackFromRight ? -1f : 1f)
                * scaledOpenStoryStep(knockbackHspeed, tickMs));
        return new MobHitKnockback(direction, airVelX);
    }

    public static float scaledOpenStoryStep(float openStoryStepValue, int tickMs) {
        return openStoryStepValue * (tickMs / 8.0f);
    }
}
