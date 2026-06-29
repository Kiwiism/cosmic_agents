package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMobKnockbackPolicyTest {
    @Test
    void shouldBlockKnockbackWhenClimbingOrDead() {
        assertFalse(AgentMobKnockbackPolicy.shouldApplyMobKnockback(true, 100, null, 0.99f));
        assertFalse(AgentMobKnockbackPolicy.shouldApplyMobKnockback(false, 0, null, 0.99f));
    }

    @Test
    void shouldApplyKnockbackWithoutPositiveStance() {
        assertTrue(AgentMobKnockbackPolicy.shouldApplyMobKnockback(false, 100, null, 0.0f));
        assertTrue(AgentMobKnockbackPolicy.shouldApplyMobKnockback(false, 100, 0, 0.0f));
        assertTrue(AgentMobKnockbackPolicy.shouldApplyMobKnockback(false, 100, -20, 0.0f));
    }

    @Test
    void shouldUseClampedStanceChanceAgainstRandomRoll() {
        assertFalse(AgentMobKnockbackPolicy.shouldApplyMobKnockback(false, 100, 60, 0.60f));
        assertTrue(AgentMobKnockbackPolicy.shouldApplyMobKnockback(false, 100, 60, 0.61f));
        assertFalse(AgentMobKnockbackPolicy.shouldApplyMobKnockback(false, 100, 200, 0.99f));
    }

    @Test
    void shouldResolveMobHitKnockbackDirectionAndScaledVelocity() {
        AgentMobKnockbackPolicy.MobHitKnockback fromRight =
                AgentMobKnockbackPolicy.resolveMobHitKnockback(
                        new Point(100, 200), new Point(150, 200), 1.5f, 16);
        assertEquals(0, fromRight.direction());
        assertEquals(-3, fromRight.airVelX());

        AgentMobKnockbackPolicy.MobHitKnockback fromLeft =
                AgentMobKnockbackPolicy.resolveMobHitKnockback(
                        new Point(100, 200), new Point(50, 200), 1.5f, 16);
        assertEquals(1, fromLeft.direction());
        assertEquals(3, fromLeft.airVelX());
    }

    @Test
    void shouldScaleOpenStoryStepByTickLength() {
        assertEquals(3.0f, AgentMobKnockbackPolicy.scaledOpenStoryStep(1.5f, 16));
    }
}
