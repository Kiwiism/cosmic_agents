package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void shouldSkipAoeClusterBonusWhenAoeUnavailableOrTargetInvalid() {
        Monster target = mobAt(100, 100, true);
        Monster near = mobAt(120, 100, true);

        assertEquals(0L, AgentCombatScoringPolicy.aoeClusterBonus(
                target, List.of(target, near), false, 3, 150, 200L));
        assertEquals(0L, AgentCombatScoringPolicy.aoeClusterBonus(
                null, List.of(target, near), true, 3, 150, 200L));
    }

    private static Monster mobAt(int x, int y, boolean alive) {
        Monster mob = mock(Monster.class);
        when(mob.getPosition()).thenReturn(new Point(x, y));
        when(mob.isAlive()).thenReturn(alive);
        return mob;
    }
}
