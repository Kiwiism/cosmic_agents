package server.life.simulation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.life.Monster;
import server.maps.MapleMap;
import server.physics.PhysicsMode;
import server.physics.foothold.FootholdPhysicsIndex;
import server.physics.foothold.FootholdSegment;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                true, false, false, false));
        fixture.session.acceptHit(fixture.agent, 10, 40, 1, 0L);

        fixture.session.advance(32_000_000L);
        assertEquals(MobMotionState.PENDING_IMPACT, fixture.session.motion());
        assertEquals(50.0, fixture.session.body().x(), 1.0e-9);

        fixture.session.advance(40_000_000L);
        assertEquals(MobMotionState.FLINCH, fixture.session.motion());
        assertTrue(fixture.session.body().x() > 50.0);
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
        Fixture fixture = fixture(new MobPhysicsProfile(0.08, 0.05, 1,
                true, true, false, false));
        fixture.session.acceptHit(fixture.agent, 0, 0, 1, 0L);
        fixture.session.advance(8_000_000L);

        assertEquals(MobMotionState.JUMPING, fixture.session.motion());
        assertTrue(fixture.session.body().velocityY() <= MobPhysicsSimulator.JUMP_FORCE);
    }

    private static Fixture fixture(MobPhysicsProfile profile) {
        MapleMap map = mock(MapleMap.class);
        Monster monster = mock(Monster.class);
        Character agent = mock(Character.class);
        when(map.isSwim()).thenReturn(false);
        when(monster.getPosition()).thenReturn(new Point(50, 100));
        when(monster.getFh()).thenReturn(1);
        when(monster.getObjectId()).thenReturn(7);
        when(agent.getPosition()).thenReturn(new Point(200, 40));
        when(agent.getId()).thenReturn(9);
        FootholdPhysicsIndex terrain = new FootholdPhysicsIndex(List.of(
                new FootholdSegment(1, 0, 0, 1, 0, false,
                        -1000, 100, 1000, 100)));
        return new Fixture(agent, new MobSimulationSession(
                map, monster, agent, profile, terrain, 0L));
    }

    private record Fixture(Character agent, MobSimulationSession session) {
    }
}
