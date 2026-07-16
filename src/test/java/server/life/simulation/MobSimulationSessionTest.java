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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MobSimulationSessionTest {
    @Test
    void qualifyingImpactUsesCapturedDirectionForExactlyThirtyOneSteps() {
        Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 10,
                true, false, false, false));
        fixture.session.acceptHit(fixture.agent, 10, 0, 1, 0L);
        when(fixture.agent.getPosition()).thenReturn(new Point(-500, 100));

        for (int step = 1; step <= 30; step++) {
            fixture.session.advance(step * 8_000_000L);
            assertEquals(MobMotionState.FLINCH, fixture.session.motion());
        }
        fixture.session.advance(31 * 8_000_000L);

        assertEquals(MobMotionState.CHASE, fixture.session.motion());
        assertTrue(fixture.session.body().x() > 50.0);
        assertEquals(1, fixture.session.knockbackDirection());
    }

    @Test
    void attackDelayDoesNotApplyKnockbackToEarlierAccumulatorSteps() {
        int originalPercent = AgentCombatConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT;
        try {
            AgentCombatConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT = 100;
            Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                    true, false, false, false));
            fixture.session.acceptHit(fixture.agent, 10, 40, 1, 0L);

            fixture.session.advance(32_000_000L);
            assertEquals(MobMotionState.PENDING_IMPACT, fixture.session.motion());
            assertEquals(50.0, fixture.session.body().x(), 1.0e-9);

            fixture.session.advance(40_000_000L);
            assertEquals(MobMotionState.FLINCH, fixture.session.motion());
            assertTrue(fixture.session.body().x() > 50.0);
        } finally {
            AgentCombatConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT = originalPercent;
        }
    }

    @Test
    void zeroImpactDelayStartsKnockbackOnFirstPhysicsStep() {
        int originalPercent = AgentCombatConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT;
        try {
            AgentCombatConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT = 0;
            Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                    true, false, false, false));
            fixture.session.acceptHit(fixture.agent, 10, 300, 1, 0L);

            fixture.session.advance(8_000_000L);

            assertEquals(MobMotionState.FLINCH, fixture.session.motion());
            assertTrue(fixture.session.body().x() > 50.0);
        } finally {
            AgentCombatConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT = originalPercent;
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
        int originalJitter = AgentCombatConfig.cfg.MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS;
        try {
            AgentCombatConfig.cfg.MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS = 0;
            Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                    true, true, false, false));
            fixture.session.acceptHit(fixture.agent, 0, 0, 1, 0L);
            fixture.session.advance(8_000_000L);

            assertEquals(MobMotionState.JUMPING, fixture.session.motion());
            assertTrue(fixture.session.body().velocityY() <= MobPhysicsSimulator.JUMP_FORCE);
        } finally {
            AgentCombatConfig.cfg.MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS = originalJitter;
        }
    }

    @Test
    void liveSpeedAndKnockbackPercentagesScaleForces() {
        int originalSpeed = AgentCombatConfig.cfg.MOB_PHYSICS_SPEED_PERCENT;
        int originalKnockback = AgentCombatConfig.cfg.MOB_PHYSICS_KNOCKBACK_PERCENT;
        try {
            AgentCombatConfig.cfg.MOB_PHYSICS_SPEED_PERCENT = 100;
            Fixture fullSpeed = fixture(new MobPhysicsProfile(0.08, 0.05, 100,
                    true, false, false, false));
            fullSpeed.session.acceptHit(fullSpeed.agent, 10, 0, 1, 0L);
            fullSpeed.session.advance(8_000_000L);

            AgentCombatConfig.cfg.MOB_PHYSICS_SPEED_PERCENT = 75;
            Fixture reducedSpeed = fixture(new MobPhysicsProfile(0.08, 0.05, 100,
                    true, false, false, false));
            reducedSpeed.session.acceptHit(reducedSpeed.agent, 10, 0, 1, 0L);
            reducedSpeed.session.advance(8_000_000L);
            assertEquals(fullSpeed.session.body().velocityX() * 0.75,
                    reducedSpeed.session.body().velocityX(), 1.0e-12);

            AgentCombatConfig.cfg.MOB_PHYSICS_KNOCKBACK_PERCENT = 100;
            Fixture fullKnockback = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                    true, false, false, false));
            fullKnockback.session.acceptHit(fullKnockback.agent, 10, 0, 1, 0L);
            fullKnockback.session.advance(8_000_000L);

            AgentCombatConfig.cfg.MOB_PHYSICS_KNOCKBACK_PERCENT = 50;
            Fixture reducedKnockback = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                    true, false, false, false));
            reducedKnockback.session.acceptHit(reducedKnockback.agent, 10, 0, 1, 0L);
            reducedKnockback.session.advance(8_000_000L);
            assertEquals(fullKnockback.session.body().velocityX() * 0.5,
                    reducedKnockback.session.body().velocityX(), 1.0e-12);
        } finally {
            AgentCombatConfig.cfg.MOB_PHYSICS_SPEED_PERCENT = originalSpeed;
            AgentCombatConfig.cfg.MOB_PHYSICS_KNOCKBACK_PERCENT = originalKnockback;
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
        int originalPercent = AgentCombatConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT;
        int originalOffset = AgentCombatConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_OFFSET_MS;
        try {
            AgentCombatConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT = 50;
            AgentCombatConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_OFFSET_MS = 0;
            Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                    true, false, false, false));
            fixture.session.acceptHit(fixture.agent, 10, 40, 1, 0L);

            fixture.session.advance(16_000_000L);
            assertEquals(MobMotionState.PENDING_IMPACT, fixture.session.motion());
            fixture.session.advance(24_000_000L);
            assertEquals(MobMotionState.FLINCH, fixture.session.motion());
        } finally {
            AgentCombatConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_PERCENT = originalPercent;
            AgentCombatConfig.cfg.MOB_PHYSICS_IMPACT_DELAY_OFFSET_MS = originalOffset;
        }
    }

    @Test
    void differentMobsDesynchronizeDirectionReactionsAndInitialJumps() {
        int originalReaction = AgentCombatConfig.cfg.MOB_PHYSICS_DIRECTION_REACTION_MAX_MS;
        int originalJitter = AgentCombatConfig.cfg.MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS;
        int originalStuck = AgentCombatConfig.cfg.MOB_PHYSICS_STUCK_DETECT_MS;
        try {
            AgentCombatConfig.cfg.MOB_PHYSICS_DIRECTION_REACTION_MAX_MS = 500;
            AgentCombatConfig.cfg.MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS = 500;
            AgentCombatConfig.cfg.MOB_PHYSICS_STUCK_DETECT_MS = 5000;

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
            AgentCombatConfig.cfg.MOB_PHYSICS_DIRECTION_REACTION_MAX_MS = originalReaction;
            AgentCombatConfig.cfg.MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS = originalJitter;
            AgentCombatConfig.cfg.MOB_PHYSICS_STUCK_DETECT_MS = originalStuck;
        }
    }

    @Test
    void edgeInsetTriggersPerMobRetreatAndNoProgressRecovery() {
        int originalChance = AgentCombatConfig.cfg.MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT;
        int originalLeft = AgentCombatConfig.cfg.MOB_PHYSICS_LEFT_EDGE_INSET_PX;
        int originalRight = AgentCombatConfig.cfg.MOB_PHYSICS_RIGHT_EDGE_INSET_PX;
        int originalStuck = AgentCombatConfig.cfg.MOB_PHYSICS_STUCK_DETECT_MS;
        int originalJitter = AgentCombatConfig.cfg.MOB_PHYSICS_BEHAVIOR_JITTER_MS;
        int originalStuckChance = AgentCombatConfig.cfg.MOB_PHYSICS_STUCK_RETREAT_CHANCE_PERCENT;
        int originalMinDistance = AgentCombatConfig.cfg.MOB_PHYSICS_RETREAT_MIN_DISTANCE_PX;
        int originalMaxDistance = AgentCombatConfig.cfg.MOB_PHYSICS_RETREAT_MAX_DISTANCE_PX;
        try {
            AgentCombatConfig.cfg.MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT = 100;
            AgentCombatConfig.cfg.MOB_PHYSICS_LEFT_EDGE_INSET_PX = 18;
            AgentCombatConfig.cfg.MOB_PHYSICS_RIGHT_EDGE_INSET_PX = 10;
            AgentCombatConfig.cfg.MOB_PHYSICS_STUCK_DETECT_MS = 500;
            AgentCombatConfig.cfg.MOB_PHYSICS_BEHAVIOR_JITTER_MS = 0;
            AgentCombatConfig.cfg.MOB_PHYSICS_STUCK_RETREAT_CHANCE_PERCENT = 100;
            AgentCombatConfig.cfg.MOB_PHYSICS_RETREAT_MIN_DISTANCE_PX = 8;
            AgentCombatConfig.cfg.MOB_PHYSICS_RETREAT_MAX_DISTANCE_PX = 8;

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
            AgentCombatConfig.cfg.MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT = originalChance;
            AgentCombatConfig.cfg.MOB_PHYSICS_LEFT_EDGE_INSET_PX = originalLeft;
            AgentCombatConfig.cfg.MOB_PHYSICS_RIGHT_EDGE_INSET_PX = originalRight;
            AgentCombatConfig.cfg.MOB_PHYSICS_STUCK_DETECT_MS = originalStuck;
            AgentCombatConfig.cfg.MOB_PHYSICS_BEHAVIOR_JITTER_MS = originalJitter;
            AgentCombatConfig.cfg.MOB_PHYSICS_STUCK_RETREAT_CHANCE_PERCENT = originalStuckChance;
            AgentCombatConfig.cfg.MOB_PHYSICS_RETREAT_MIN_DISTANCE_PX = originalMinDistance;
            AgentCombatConfig.cfg.MOB_PHYSICS_RETREAT_MAX_DISTANCE_PX = originalMaxDistance;
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
