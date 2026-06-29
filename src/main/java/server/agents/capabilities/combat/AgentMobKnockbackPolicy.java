package server.agents.capabilities.combat;

import java.awt.Point;

public final class AgentMobKnockbackPolicy {
    public record MobHitKnockback(int direction, int airVelX) {
    }

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
