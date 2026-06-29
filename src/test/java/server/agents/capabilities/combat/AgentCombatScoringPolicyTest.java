package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Point;
import java.util.List;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.life.Monster;

class AgentCombatScoringPolicyTest {
    @Test
    void shouldCapExpectedDamageAtCurrentHp() {
        assertEquals(50.0d, AgentCombatScoringPolicy.capDamageByCurrentHp(100.0d, 50));
        assertEquals(40.0d, AgentCombatScoringPolicy.capDamageByCurrentHp(40.0d, 50));
        assertEquals(0.0d, AgentCombatScoringPolicy.capDamageByCurrentHp(100.0d, 0));
    }

    @Test
    void shouldEstimateLocalTravelCostFromWalkVelocityAndVerticalPenalty() {
        long expected = Math.round(100 * 1000.0 / AgentMovementProfile.base().walkVelocityPxs()) + 20 * 4L;

        assertEquals(expected, AgentCombatScoringPolicy.estimateLocalTravelCostMs(
                new Point(100, 100), new Point(200, 120), AgentMovementProfile.base()));
    }

    @Test
    void shouldScoreLocalTargetsWithLegacyDistanceLevelAndFootholdPenalties() {
        assertEquals(100L, AgentCombatScoringPolicy.localTargetScore(
                new Point(100, 100), new Point(200, 100), true, 50));
        assertEquals(100L + 60L * 8L + 600L, AgentCombatScoringPolicy.localTargetScore(
                new Point(100, 100), new Point(200, 160), true, 50));
        assertEquals(100L + 1200L, AgentCombatScoringPolicy.localTargetScore(
                new Point(100, 100), new Point(200, 100), false, 50));
    }

    @Test
    void shouldCapAoeClusterBonusByMobCountMinusOne() {
        Monster target = mobAt(100, 100, true);
        Monster nearOne = mobAt(120, 100, true);
        Monster nearTwo = mobAt(130, 100, true);
        Monster nearThree = mobAt(140, 100, true);
        Monster far = mobAt(500, 100, true);

        assertEquals(400L, AgentCombatScoringPolicy.aoeClusterBonus(
                target, List.of(target, nearOne, nearTwo, nearThree, far),
                true, 3, 150, 200L));
    }

    @Test
    void shouldUseLegacyAoeClusterDefaults() {
        Monster target = mobAt(100, 100, true);
        Monster nearOne = mobAt(120, 100, true);
        Monster nearTwo = mobAt(130, 100, true);
        Monster far = mobAt(260, 100, true);

        assertEquals(400L, AgentCombatScoringPolicy.legacyAoeClusterBonus(
                target, List.of(target, nearOne, nearTwo, far), true, 3));
    }

    @Test
    void shouldCompareAoeScoreAgainstBestSingleTargetScore() {
        assertTrue(AgentCombatScoringPolicy.aoeBeatsSingleTargetScore(120, 2, 2, 200L));
        assertFalse(AgentCombatScoringPolicy.aoeBeatsSingleTargetScore(100, 1, 2, 200L));
        assertFalse(AgentCombatScoringPolicy.aoeBeatsSingleTargetScore(-20, 5, 5, 100L));
    }

    @Test
    void shouldBuildSingleTargetScoreFromDamageAndHitCountWithBasicAttackFloor() {
        assertTrue(AgentCombatScoringPolicy.aoeBeatsSingleTargetScore(120, 2, 2, 150, 2));
        assertFalse(AgentCombatScoringPolicy.aoeBeatsSingleTargetScore(80, 1, 1, 0, 1));
    }

    @Test
    void shouldSkipAoeClusterBonusWhenAoeUnavailableOrTargetInvalid() {
        Monster target = mobAt(100, 100, true);
        Monster near = mobAt(120, 100, true);

        assertEquals(0L, AgentCombatScoringPolicy.aoeClusterBonus(
                target, List.of(target, near), false, 3, 150, 200L));
        assertEquals(0L, AgentCombatScoringPolicy.aoeClusterBonus(
                null, List.of(target, near), true, 3, 150, 200L));
    }

    @Test
    void shouldDetectAoeSingleTargetingWhenPlanHasRoomForMoreTargets() {
        assertTrue(AgentCombatScoringPolicy.isAoeSingleTargeting(1001004, 1, true, 1001005, 6));
        assertFalse(AgentCombatScoringPolicy.isAoeSingleTargeting(1001005, 1, true, 1001005, 6));
        assertFalse(AgentCombatScoringPolicy.isAoeSingleTargeting(1001004, 6, true, 1001005, 6));
        assertFalse(AgentCombatScoringPolicy.isAoeSingleTargeting(1001004, 1, false, 1001005, 6));
    }

    @Test
    void shouldCountAoeClusterSizeCappedAtSkillMobCount() {
        Monster target = mobAt(100, 100, true);
        Monster nearOne = mobAt(120, 100, true);
        Monster nearTwo = mobAt(130, 100, true);
        Monster far = mobAt(500, 100, true);

        assertEquals(3, AgentCombatScoringPolicy.cappedAoeClusterSize(
                target, List.of(target, nearOne, nearTwo, far), true, 6, 150));
        assertEquals(2, AgentCombatScoringPolicy.cappedAoeClusterSize(
                target, List.of(target, nearOne, nearTwo, far), true, 2, 150));
        assertEquals(0, AgentCombatScoringPolicy.cappedAoeClusterSize(
                target, List.of(target, nearOne), false, 6, 150));
    }

    @Test
    void shouldUseLegacyAoeClusterSizeDefaults() {
        Monster target = mobAt(100, 100, true);
        Monster nearOne = mobAt(120, 100, true);
        Monster nearTwo = mobAt(130, 100, true);
        Monster far = mobAt(260, 100, true);

        assertEquals(3, AgentCombatScoringPolicy.legacyCappedAoeClusterSize(
                target, List.of(target, nearOne, nearTwo, far), true, 6));
        assertEquals(List.of(target, nearOne, nearTwo),
                AgentCombatScoringPolicy.legacyClusterMonsters(
                        target, List.of(target, nearOne, nearTwo, far)));
    }

    @Test
    void shouldBuildAoeClusterAroundPrimaryTarget() {
        Monster target = mobAt(100, 100, true);
        Monster near = mobAt(130, 100, true);
        Monster deadNear = mobAt(140, 100, false);
        Monster far = mobAt(400, 100, true);

        assertEquals(List.of(target, near),
                AgentCombatScoringPolicy.clusterMonsters(target, List.of(target, near, deadNear, far), 150));
    }

    @Test
    void shouldSelectNearestMonsterToPoint() {
        Monster far = mobAt(100, 100, true);
        Monster near = mobAt(130, 100, true);

        assertEquals(near, AgentCombatScoringPolicy.nearestMonster(List.of(far, near), 125, 100));
    }

    @Test
    void shouldCalculateClusterCentroidXLikeLegacyAoeReposition() {
        assertEquals(200, AgentCombatScoringPolicy.clusterCentroidX(List.of(
                mobAt(140, 200, true),
                mobAt(200, 200, true),
                mobAt(260, 200, true))));
        assertEquals(101, AgentCombatScoringPolicy.clusterCentroidX(List.of(
                mobAt(100, 200, true),
                mobAt(103, 200, true))));
    }

    @Test
    void shouldBoundAoeRepositionShiftByConfiguredDistance() {
        assertEquals(80, AgentCombatScoringPolicy.boundedRepositionShift(300, 100.2d, 80));
        assertEquals(-80, AgentCombatScoringPolicy.boundedRepositionShift(0, 100.8d, 80));
        assertEquals(20, AgentCombatScoringPolicy.boundedRepositionShift(120, 100.2d, 80));
    }

    @Test
    void shouldDetectAoeRepositionArrivalWindow() {
        assertTrue(AgentCombatScoringPolicy.isWithinRepositionArrival(10, 10));
        assertTrue(AgentCombatScoringPolicy.isWithinRepositionArrival(-10, 10));
        assertFalse(AgentCombatScoringPolicy.isWithinRepositionArrival(11, 10));
    }

    private static Monster mobAt(int x, int y, boolean alive) {
        Monster mob = mock(Monster.class);
        when(mob.getPosition()).thenReturn(new Point(x, y));
        when(mob.isAlive()).thenReturn(alive);
        return mob;
    }
}
