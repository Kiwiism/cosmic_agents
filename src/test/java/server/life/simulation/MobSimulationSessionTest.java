package server.life.simulation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.life.Monster;
import server.maps.MapleMap;
import server.physics.PhysicsMode;
import server.physics.foothold.FootholdPhysicsIndex;
import server.physics.foothold.FootholdSegment;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MobSimulationSessionTest {
    @Test
    void agentHitLeaseExpiresFromMostRecentAcceptedHit() {
        Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                true, false, false, false));
        fixture.session.acceptHit(fixture.agent, 10, 0, 1, 1_000_000_000L);

        assertFalse(fixture.session.agentHitLeaseExpired(7_999_000_000L, 7_000));
        assertTrue(fixture.session.agentHitLeaseExpired(8_000_000_000L, 7_000));
        assertFalse(fixture.session.agentHitLeaseExpired(100_000_000_000L, 0),
                "zero disables lease expiry");

        fixture.session.acceptHit(fixture.agent, 10, 0, 1, 7_000_000_000L);
        assertFalse(fixture.session.agentHitLeaseExpired(13_999_000_000L, 7_000));
        assertTrue(fixture.session.agentHitLeaseExpired(14_000_000_000L, 7_000));
    }

    @Test
    void moveActivityRefreshesAfterWzCycleAndOnFacingChange() {
        Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                true, false, false, false));

        fixture.session.setMotion(MobMotionState.CHASE);
        assertEquals(0, fixture.session.rawActivityForPublication(0, 1_000_000L));
        assertEquals(-1, fixture.session.rawActivityForPublication(0, 700_000_000L));
        assertEquals(0, fixture.session.rawActivityForPublication(0, 721_000_000L),
                "move activity must refresh after one WZ animation cycle");
        assertEquals(1, fixture.session.rawActivityForPublication(1, 722_000_000L));
        assertEquals(-1, fixture.session.rawActivityForPublication(1, 723_000_000L));

        fixture.session.setMotion(MobMotionState.FLINCH);
        assertEquals(-1, fixture.session.rawActivityForPublication(1, 724_000_000L));
        assertEquals(1, fixture.session.rawActivityForPublication(1, 1_442_000_000L),
                "stationary flinch must continue the WZ move-animation loop");
        fixture.session.setMotion(MobMotionState.CHASE);
        assertEquals(-1, fixture.session.rawActivityForPublication(1, 1_443_000_000L));
    }

    @Test
    void chaseForceRampsUpAfterFlinchRecovery() {
        int originalRecovery = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_FLINCH_RECOVERY_MS;
        int originalRamp = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_POST_FLINCH_CHASE_RAMP_MS;
        try {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_FLINCH_RECOVERY_MS = 8;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_POST_FLINCH_CHASE_RAMP_MS = 24;
            Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                    true, false, false, false));
            fixture.session.acceptHit(fixture.agent, 10, 0, 1, 0L);
            for (int step = 1; step <= 32; step++) {
                fixture.session.advance(step * 8_000_000L);
            }

            assertEquals(MobMotionState.CHASE, fixture.session.motion());
            assertEquals(1.0 / 3.0, fixture.session.consumeChaseRampMultiplier(), 1.0e-12);
            assertEquals(2.0 / 3.0, fixture.session.consumeChaseRampMultiplier(), 1.0e-12);
            assertEquals(1.0, fixture.session.consumeChaseRampMultiplier(), 1.0e-12);
            assertEquals(1.0, fixture.session.consumeChaseRampMultiplier(), 1.0e-12);
        } finally {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_FLINCH_RECOVERY_MS = originalRecovery;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_POST_FLINCH_CHASE_RAMP_MS = originalRamp;
        }
    }

    @Test
    void qualifyingImpactKnocksBackThenRecoversWithoutMoving() {
        int originalRecovery = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_FLINCH_RECOVERY_MS;
        try {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_FLINCH_RECOVERY_MS = 16;
            Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 10,
                    true, false, false, false));
            fixture.session.body().setVelocity(-0.75, 0.0);
            fixture.session.acceptHit(fixture.agent, 10, 0, 1, 0L);
            assertEquals(-0.75, fixture.session.body().velocityX(), 1.0e-12,
                    "scheduling a hit must not mutate motion before impact");
            when(fixture.agent.getPosition()).thenReturn(new Point(-500, 100));

            fixture.session.advance(8_000_000L);
            assertEquals(MobMotionState.KNOCKBACK, fixture.session.motion());
            assertTrue(fixture.session.body().velocityX() > 0.0,
                    "knockback must replace opposing chase momentum");
            for (int step = 2; step <= 30; step++) {
                fixture.session.advance(step * 8_000_000L);
                assertEquals(MobMotionState.KNOCKBACK, fixture.session.motion());
            }
            fixture.session.advance(31 * 8_000_000L);
            assertEquals(MobMotionState.FLINCH, fixture.session.motion());
            double recoveryX = fixture.session.body().x();
            assertEquals(0.0, fixture.session.body().velocityX(), 1.0e-12);

            fixture.session.advance(32 * 8_000_000L);
            assertEquals(MobMotionState.FLINCH, fixture.session.motion());
            assertEquals(recoveryX, fixture.session.body().x(), 1.0e-12);
            fixture.session.advance(33 * 8_000_000L);

            assertEquals(MobMotionState.CHASE, fixture.session.motion());
            assertEquals(recoveryX, fixture.session.body().x(), 1.0e-12);
            assertTrue(recoveryX > 50.0);
            assertEquals(1, fixture.session.knockbackDirection());
        } finally {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_FLINCH_RECOVERY_MS = originalRecovery;
        }
    }

    @Test
    void additionalHitsCannotRestartOrReverseAnActiveReaction() {
        int originalRecovery = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_FLINCH_RECOVERY_MS;
        try {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_FLINCH_RECOVERY_MS = 16;
            Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                    true, false, false, false));
            Character secondAgent = mock(Character.class);
            when(secondAgent.getPosition()).thenReturn(new Point(500, 100));

            fixture.session.acceptHit(fixture.agent, 10, 0, 1, 0L);
            fixture.session.advance(8_000_000L);
            double knockbackVelocity = fixture.session.body().velocityX();

            fixture.session.acceptHit(secondAgent, 10, 200, -1, 8_000_000L);
            assertEquals(MobMotionState.KNOCKBACK, fixture.session.motion());
            assertEquals(1, fixture.session.knockbackDirection());
            assertEquals(knockbackVelocity, fixture.session.body().velocityX(), 1.0e-12,
                    "a second source must not stack or reverse active knockback");
            assertEquals(secondAgent, fixture.session.agent(),
                    "the latest hitter may still become the aggro target");

            for (int step = 2; step <= 31; step++) {
                fixture.session.advance(step * 8_000_000L);
            }
            assertEquals(MobMotionState.FLINCH, fixture.session.motion());
            double recoveryX = fixture.session.body().x();

            fixture.session.acceptHit(fixture.agent, 10, 0, -1, 248_000_000L);
            assertEquals(MobMotionState.FLINCH, fixture.session.motion());
            assertEquals(1, fixture.session.knockbackDirection());
            fixture.session.advance(256_000_000L);
            assertEquals(MobMotionState.FLINCH, fixture.session.motion());
            assertEquals(recoveryX, fixture.session.body().x(), 1.0e-12);
            fixture.session.advance(264_000_000L);
            assertEquals(MobMotionState.CHASE, fixture.session.motion());

            fixture.session.acceptHit(secondAgent, 10, 0, -1, 264_000_000L);
            assertEquals(MobMotionState.PENDING_IMPACT, fixture.session.motion(),
                    "new knockback may be scheduled after recovery completes");
            assertEquals(-1, fixture.session.knockbackDirection());
        } finally {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_FLINCH_RECOVERY_MS = originalRecovery;
        }
    }

    @Test
    void attackDelayDoesNotApplyKnockbackToEarlierAccumulatorSteps() {
        int originalPercent = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT;
        int originalOffset = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_OFFSET_MS;
        try {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT = 100;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_OFFSET_MS = 0;
            Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                    true, false, false, false));
            fixture.session.acceptHit(fixture.agent, 10, 40, 1, 0L);

            fixture.session.advance(32_000_000L);
            assertEquals(MobMotionState.PENDING_IMPACT, fixture.session.motion());
            assertEquals(50.0, fixture.session.body().x(), 1.0e-9);

            fixture.session.advance(40_000_000L);
            assertEquals(MobMotionState.KNOCKBACK, fixture.session.motion());
            assertTrue(fixture.session.body().x() > 50.0);
        } finally {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT = originalPercent;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_OFFSET_MS = originalOffset;
        }
    }

    @Test
    void zeroImpactDelayStartsKnockbackOnFirstPhysicsStep() {
        int originalPercent = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT;
        try {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT = 0;
            Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                    true, false, false, false));
            fixture.session.acceptHit(fixture.agent, 10, 300, 1, 0L);

            fixture.session.advance(8_000_000L);

            assertEquals(MobMotionState.KNOCKBACK, fixture.session.motion());
            assertTrue(fixture.session.body().x() > 50.0);
        } finally {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT = originalPercent;
        }
    }

    @Test
    void damageBelowPushedAcquiresChaseWithoutKnockback() {
        Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 100,
                true, false, false, false));
        fixture.session.acceptHit(fixture.agent, 99, 0, -1, 0L);
        fixture.session.advance(8_000_000L);

        assertEquals(MobMotionState.CHASE, fixture.session.motion());
        assertTrue(fixture.session.body().velocityX() > 0.0);
    }

    @Test
    void flyingPursuitAcceleratesOnBothAxesAndThenDecelerates() {
        Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                true, false, true, false));
        fixture.session.acceptHit(fixture.agent, 0, 0, 1, 0L);
        fixture.session.advance(8_000_000L);
        assertTrue(fixture.session.body().velocityX() > 0.0);
        assertTrue(fixture.session.body().velocityY() < 0.0);

        when(fixture.agent.getPosition()).thenReturn(new Point(
                (int) fixture.session.body().x(), (int) fixture.session.body().y()));
        double previous = fixture.session.body().velocityX();
        fixture.session.advance(16_000_000L);
        assertTrue(fixture.session.body().velocityX() < previous);
    }

    @Test
    void fixedProfileNeverMoves() {
        Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                false, false, false, true));
        fixture.session.acceptHit(fixture.agent, 100, 0, 1, 0L);
        fixture.session.advance(50_000_000L);
        assertEquals(PhysicsMode.FIXED, fixture.session.body().mode());
        assertEquals(50.0, fixture.session.body().x(), 1.0e-9);
    }

    @Test
    void jumpCapableGroundMobUsesReferenceForceForHigherForwardTarget() {
        int originalJitter = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS;
        try {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS = 0;
            Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                    true, true, false, false));
            fixture.session.acceptHit(fixture.agent, 0, 0, 1, 0L);
            fixture.session.advance(8_000_000L);

            assertEquals(MobMotionState.JUMPING, fixture.session.motion());
            assertTrue(fixture.session.body().velocityY() <= MobPhysicsSimulator.JUMP_FORCE);
        } finally {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS = originalJitter;
        }
    }

    @Test
    void liveSpeedAndKnockbackPercentagesScaleForces() {
        int originalSpeed = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_SPEED_PERCENT;
        int originalKnockback = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_KNOCKBACK_PERCENT;
        try {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_SPEED_PERCENT = 100;
            Fixture fullSpeed = fixture(new MobPhysicsProfile(0.08, 0.05, 100,
                    true, false, false, false));
            fullSpeed.session.acceptHit(fullSpeed.agent, 10, 0, 1, 0L);
            fullSpeed.session.advance(8_000_000L);

            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_SPEED_PERCENT = 75;
            Fixture reducedSpeed = fixture(new MobPhysicsProfile(0.08, 0.05, 100,
                    true, false, false, false));
            reducedSpeed.session.acceptHit(reducedSpeed.agent, 10, 0, 1, 0L);
            reducedSpeed.session.advance(8_000_000L);
            assertEquals(fullSpeed.session.body().velocityX() * 0.75,
                    reducedSpeed.session.body().velocityX(), 1.0e-12);

            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_KNOCKBACK_PERCENT = 100;
            Fixture fullKnockback = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                    true, false, false, false));
            fullKnockback.session.acceptHit(fullKnockback.agent, 10, 0, 1, 0L);
            fullKnockback.session.advance(8_000_000L);

            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_KNOCKBACK_PERCENT = 50;
            Fixture reducedKnockback = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                    true, false, false, false));
            reducedKnockback.session.acceptHit(reducedKnockback.agent, 10, 0, 1, 0L);
            reducedKnockback.session.advance(8_000_000L);
            assertEquals(fullKnockback.session.body().velocityX() * 0.5,
                    reducedKnockback.session.body().velocityX(), 1.0e-12);
        } finally {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_SPEED_PERCENT = originalSpeed;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_KNOCKBACK_PERCENT = originalKnockback;
        }
    }

    @Test
    void differentMobSeedsDoNotVaryChaseSpeed() {
        Fixture first = fixture(new MobPhysicsProfile(0.08, 0.05, 100,
                true, false, false, false), 7, 50, -1000, 1000);
        Fixture second = fixture(new MobPhysicsProfile(0.08, 0.05, 100,
                true, false, false, false), 8, 50, -1000, 1000);
        first.session.acceptHit(first.agent, 10, 0, 1, 0L);
        second.session.acceptHit(second.agent, 10, 0, 1, 0L);

        first.session.advance(8_000_000L);
        second.session.advance(8_000_000L);

        assertEquals(first.session.body().velocityX(),
                second.session.body().velocityX(), 1.0e-12);
    }

    @Test
    void impactDelayPercentageIsAppliedBeforeFlinch() {
        int originalPercent = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT;
        int originalOffset = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_OFFSET_MS;
        try {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT = 50;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_OFFSET_MS = 0;
            Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                    true, false, false, false));
            fixture.session.acceptHit(fixture.agent, 10, 40, 1, 0L);

            fixture.session.advance(16_000_000L);
            assertEquals(MobMotionState.PENDING_IMPACT, fixture.session.motion());
            fixture.session.advance(24_000_000L);
            assertEquals(MobMotionState.KNOCKBACK, fixture.session.motion());
        } finally {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT = originalPercent;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_OFFSET_MS = originalOffset;
        }
    }

    @Test
    void differentMobsDesynchronizeDirectionReactionsAndInitialJumps() {
        int originalReaction = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_DIRECTION_REACTION_MAX_MS;
        int originalJitter = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS;
        int originalStuck = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_STUCK_DETECT_MS;
        try {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_DIRECTION_REACTION_MAX_MS = 500;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS = 500;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_STUCK_DETECT_MS = 5000;

            Fixture first = fixture(new MobPhysicsProfile(0.08, 0.05, 100,
                    true, false, false, false), 7, 50, -1000, 1000);
            Fixture second = fixture(new MobPhysicsProfile(0.08, 0.05, 100,
                    true, false, false, false), 8, 50, -1000, 1000);
            long firstTurn = reversalTime(first);
            long secondTurn = reversalTime(second);
            assertNotEquals(firstTurn, secondTurn);

            Fixture firstJumper = fixture(new MobPhysicsProfile(0.08, 0.05, 100,
                    true, true, false, false), 7, 50, -1000, 1000);
            Fixture secondJumper = fixture(new MobPhysicsProfile(0.08, 0.05, 100,
                    true, true, false, false), 8, 50, -1000, 1000);
            long firstJump = firstJumpTime(firstJumper);
            long secondJump = firstJumpTime(secondJumper);
            assertNotEquals(firstJump, secondJump);
        } finally {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_DIRECTION_REACTION_MAX_MS = originalReaction;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS = originalJitter;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_STUCK_DETECT_MS = originalStuck;
        }
    }

    @Test
    void edgeInsetTriggersPerMobRetreatAndNoProgressRecovery() {
        int originalChance = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT;
        int originalLeft = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_LEFT_EDGE_INSET_PX;
        int originalRight = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_RIGHT_EDGE_INSET_PX;
        int originalStuck = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_STUCK_DETECT_MS;
        int originalJitter = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_BEHAVIOR_JITTER_MS;
        int originalStuckChance = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_STUCK_RETREAT_CHANCE_PERCENT;
        int originalMinDistance = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_RETREAT_MIN_DISTANCE_PX;
        int originalMaxDistance = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_RETREAT_MAX_DISTANCE_PX;
        try {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT = 100;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_LEFT_EDGE_INSET_PX = 18;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_RIGHT_EDGE_INSET_PX = 10;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_STUCK_DETECT_MS = 500;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_BEHAVIOR_JITTER_MS = 0;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_STUCK_RETREAT_CHANCE_PERCENT = 100;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_RETREAT_MIN_DISTANCE_PX = 8;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_RETREAT_MAX_DISTANCE_PX = 8;

            Fixture edge = fixture(new MobPhysicsProfile(0.08, 0.05, 100,
                    true, false, false, false), 7, 80, 0, 100);
            edge.session.acceptHit(edge.agent, 10, 0, 1, 0L);
            for (int step = 1; step <= 500 && !edge.session.hasTemporaryBehavior(); step++) {
                edge.session.advance(step * 8_000_000L);
            }
            assertTrue(edge.session.hasTemporaryBehavior());
            assertEquals(-1, edge.session.temporaryDirection());
            assertEquals(8.0, edge.session.temporaryRetreatDistancePx());
            assertEquals(90.0, edge.session.body().x(), 1.0e-9);

            Fixture stuck = fixture(new MobPhysicsProfile(0.0, 0.05, 100,
                    true, false, false, false), 9, 50, -1000, 1000);
            stuck.session.acceptHit(stuck.agent, 10, 0, 1, 0L);
            for (int step = 1; step <= 80; step++) {
                stuck.session.advance(step * 8_000_000L);
            }
            assertTrue(stuck.session.hasTemporaryBehavior());
            assertNotEquals(0, stuck.session.temporaryDirection());
        } finally {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT = originalChance;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_LEFT_EDGE_INSET_PX = originalLeft;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_RIGHT_EDGE_INSET_PX = originalRight;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_STUCK_DETECT_MS = originalStuck;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_BEHAVIOR_JITTER_MS = originalJitter;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_STUCK_RETREAT_CHANCE_PERCENT = originalStuckChance;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_RETREAT_MIN_DISTANCE_PX = originalMinDistance;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_RETREAT_MAX_DISTANCE_PX = originalMaxDistance;
        }
    }

    @Test
    void pendingImpactPreservesAirborneVelocityAndGravity() {
        Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                true, false, false, false));
        fixture.session.body().setPosition(50.0, 80.0);
        fixture.session.body().setGrounded(false);
        fixture.session.body().setVelocity(0.75, -2.0);

        fixture.session.acceptHit(fixture.agent, 10, 40, 1, 0L);

        assertEquals(0.75, fixture.session.body().velocityX(), 1.0e-12);
        assertEquals(-2.0, fixture.session.body().velocityY(), 1.0e-12);
        fixture.session.advance(8_000_000L);
        assertTrue(fixture.session.body().x() > 50.0);
        assertTrue(fixture.session.body().y() < 80.0);
        assertTrue(fixture.session.body().velocityY() > -2.0,
                "gravity must continue during the impact delay");
    }

    @Test
    void airborneKnockbackAddsHorizontalImpulseWithoutFreezingJumpArc() {
        Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                true, false, false, false));
        fixture.session.body().setPosition(50.0, 80.0);
        fixture.session.body().setGrounded(false);
        fixture.session.body().setVelocity(0.75, -2.0);

        fixture.session.acceptHit(fixture.agent, 10, 0, 1, 0L);
        fixture.session.advance(8_000_000L);

        assertEquals(MobMotionState.KNOCKBACK, fixture.session.motion());
        assertTrue(fixture.session.body().velocityX() > 0.75);
        assertTrue(fixture.session.body().velocityY() < 0.0,
                "an upward jump must remain upward when the hit lands");
    }

    @Test
    void constructorRejectsStaleExistingFootholdOutsideMobPosition() {
        MapleMap map = mock(MapleMap.class);
        Monster monster = mock(Monster.class);
        Character agent = mock(Character.class);
        when(map.isSwim()).thenReturn(false);
        when(map.getId()).thenReturn(100000000);
        when(monster.getPosition()).thenReturn(new Point(50, 100));
        when(monster.getFh()).thenReturn(1);
        when(monster.getObjectId()).thenReturn(7);
        when(agent.getPosition()).thenReturn(new Point(200, 100));
        FootholdSegment stale = new FootholdSegment(
                1, 0, 0, 1, 0, false, 0, 100, 10, 100);
        FootholdSegment actual = new FootholdSegment(
                2, 0, 0, 1, 0, false, 40, 100, 100, 100);

        MobSimulationSession session = new MobSimulationSession(
                map, monster, agent,
                new MobPhysicsProfile(0.08, 0.05, 1, true, false, false, false),
                new FootholdPhysicsIndex(List.of(stale, actual)), 0L);

        assertEquals(2, session.body().footholdId());
        assertTrue(session.body().grounded());
        assertEquals(100.0, session.body().y(), 1.0e-12);
    }

    @Test
    void constructorRestoresFreshClientVelocityDuringAirborneHandoff() {
        MapleMap map = mock(MapleMap.class);
        Monster monster = mock(Monster.class);
        Character agent = mock(Character.class);
        when(map.isSwim()).thenReturn(false);
        when(map.getId()).thenReturn(100000000);
        when(monster.getPosition()).thenReturn(new Point(50, 80));
        when(monster.getFh()).thenReturn(1);
        when(monster.getObjectId()).thenReturn(7);
        when(monster.getLastClientMovement()).thenReturn(new MobMovementSnapshot(
                50, 80, 0.75, -2.0, 1, 2, 0L));
        when(agent.getPosition()).thenReturn(new Point(200, 100));
        FootholdSegment platform = new FootholdSegment(
                1, 0, 0, 1, 0, false, 0, 100, 100, 100);

        MobSimulationSession session = new MobSimulationSession(
                map, monster, agent,
                new MobPhysicsProfile(0.08, 0.05, 1, true, false, false, false),
                new FootholdPhysicsIndex(List.of(platform)), 0L);

        assertFalse(session.body().grounded());
        assertEquals(0.75, session.body().velocityX(), 1.0e-12);
        assertEquals(-2.0, session.body().velocityY(), 1.0e-12);
    }

    @Test
    void groundedKnockbackReportsEdgeClampWithoutGroundLoss() {
        Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                true, false, false, false), 7, 99, 0, 100);
        fixture.session.acceptHit(fixture.agent, 10, 0, 1, 0L);
        int edgeClamps = 0;
        int groundLosses = 0;

        for (int step = 1; step <= MobPhysicsSimulator.KNOCKBACK_STEPS; step++) {
            MobSimulationSession.AdvanceResult result =
                    fixture.session.advance(step * 8_000_000L);
            edgeClamps += result.edgeClamps();
            groundLosses += result.unexpectedGroundLosses();
        }

        assertTrue(edgeClamps > 0);
        assertEquals(0, groundLosses);
        assertTrue(fixture.session.body().grounded());
        assertTrue(fixture.session.body().x() <= 100.0);
    }

    @Test
    void observedPublicationDoesNotChangeKnockbackTrajectory() {
        int originalImpactDelay = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT;
        try {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT = 0;
            MobPhysicsProfile profile = new MobPhysicsProfile(0.08, 0.05, 100,
                    true, false, false, false);
            Fixture observed = fixture(profile, 7, 50, -1000, 1000);
            Fixture unobserved = fixture(profile, 7, 50, -1000, 1000);
            observed.session.acceptHit(observed.agent, 10, 0, 1, 0L);
            unobserved.session.acceptHit(unobserved.agent, 10, 0, 1, 0L);

            for (int step = 1; step <= 40; step++) {
                long nowNanos = step * 8_000_000L;
                observed.session.advance(nowNanos);
                observed.session.rawActivityForPublication(
                        observed.session.body().velocityX() < 0.0 ? -1 : 1,
                        nowNanos);
                unobserved.session.advance(nowNanos);

                assertEquals(observed.session.body().x(), unobserved.session.body().x(), 1.0e-12);
                assertEquals(observed.session.body().y(), unobserved.session.body().y(), 1.0e-12);
                assertEquals(observed.session.body().velocityX(), unobserved.session.body().velocityX(), 1.0e-12);
                assertEquals(observed.session.body().velocityY(), unobserved.session.body().velocityY(), 1.0e-12);
                assertEquals(observed.session.body().footholdId(), unobserved.session.body().footholdId());
                assertEquals(observed.session.body().grounded(), unobserved.session.body().grounded());
                assertEquals(observed.session.motion(), unobserved.session.motion());
            }
        } finally {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT = originalImpactDelay;
        }
    }

    private static Fixture fixture(MobPhysicsProfile profile) {
        return fixture(profile, 7, 50, -1000, 1000);
    }

    private static Fixture fixture(MobPhysicsProfile profile, int objectId,
                                   int x, int left, int right) {
        MapleMap map = mock(MapleMap.class);
        Monster monster = mock(Monster.class);
        Character agent = mock(Character.class);
        when(map.isSwim()).thenReturn(false);
        when(map.getId()).thenReturn(100000000);
        when(monster.getPosition()).thenReturn(new Point(x, 100));
        when(monster.getFh()).thenReturn(1);
        when(monster.getObjectId()).thenReturn(objectId);
        when(monster.getAnimationTime("move")).thenReturn(720);
        when(monster.getAnimationTime("fly")).thenReturn(720);
        when(agent.getPosition()).thenReturn(new Point(200, 40));
        when(agent.getId()).thenReturn(9);
        FootholdPhysicsIndex terrain = new FootholdPhysicsIndex(List.of(
                new FootholdSegment(1, 0, 0, 1, 0, false,
                        left, 100, right, 100)));
        return new Fixture(agent, new MobSimulationSession(
                map, monster, agent, profile, terrain, 0L));
    }

    private static long reversalTime(Fixture fixture) {
        fixture.session.acceptHit(fixture.agent, 10, 0, 1, 0L);
        fixture.session.advance(8_000_000L);
        when(fixture.agent.getPosition()).thenReturn(new Point(-200, 100));
        for (int step = 2; step <= 100; step++) {
            fixture.session.advance(step * 8_000_000L);
            if (fixture.session.body().velocityX() < 0.0) return step * 8L;
        }
        throw new AssertionError("mob did not reverse within 800 ms");
    }

    private static long firstJumpTime(Fixture fixture) {
        fixture.session.acceptHit(fixture.agent, 10, 0, 1, 0L);
        for (int step = 1; step <= 80; step++) {
            fixture.session.advance(step * 8_000_000L);
            if (fixture.session.motion() == MobMotionState.JUMPING) return step * 8L;
        }
        throw new AssertionError("mob did not jump within configured initial jitter");
    }

    private record Fixture(Character agent, MobSimulationSession session) {
    }
}
