package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import server.life.Monster;
import server.maps.Foothold;

class AgentCombatGrindTargetPolicyTest {
    @Test
    void shouldAcceptSameFootholdWithoutResolvingGraphRegion() {
        AtomicBoolean resolved = new AtomicBoolean(false);

        assertTrue(AgentCombatGrindTargetPolicy.isLocalCombatTarget(
                foothold(7), foothold(7), true, () -> {
                    resolved.set(true);
                    return -1;
                }, 3));
        assertFalse(resolved.get());
    }

    @Test
    void shouldRejectDifferentFootholdWhenGraphUnavailableWithoutResolvingRegion() {
        AtomicBoolean resolved = new AtomicBoolean(false);

        assertFalse(AgentCombatGrindTargetPolicy.isLocalCombatTarget(
                foothold(7), foothold(8), false, () -> {
                    resolved.set(true);
                    return 3;
                }, 3));
        assertFalse(resolved.get());
    }

    @Test
    void shouldAcceptTargetsInSameNavigationRegion() {
        assertTrue(AgentCombatGrindTargetPolicy.isLocalCombatTarget(
                foothold(7), foothold(8), true, () -> 3, 3));
    }

    @Test
    void shouldRejectMissingOrDifferentNavigationRegion() {
        assertFalse(AgentCombatGrindTargetPolicy.isLocalCombatTarget(
                null, null, true, () -> -1, 3));
        assertFalse(AgentCombatGrindTargetPolicy.isLocalCombatTarget(
                null, null, true, () -> 4, 3));
    }

    @Test
    void shouldPickBestTargetByLegacyGraphLocalAndDistanceOrder() {
        Monster graphWinner = mock(Monster.class);
        Monster localWinner = mock(Monster.class);
        Monster distanceWinner = mock(Monster.class);
        Monster loser = mock(Monster.class);

        List<AgentScoredGrindTarget> targets = new ArrayList<>(List.of(
                new AgentScoredGrindTarget(loser, 30, 1, 1.0),
                new AgentScoredGrindTarget(distanceWinner, 10, 5, 2.0),
                new AgentScoredGrindTarget(localWinner, 10, 4, 99.0),
                new AgentScoredGrindTarget(graphWinner, 5, 100, 100.0)));

        assertEquals(graphWinner, AgentCombatGrindTargetPolicy.pickFromBestTargets(targets));
        assertEquals(graphWinner, targets.get(0).monster());

        targets = new ArrayList<>(List.of(
                new AgentScoredGrindTarget(loser, 10, 5, 3.0),
                new AgentScoredGrindTarget(distanceWinner, 10, 5, 2.0)));
        assertEquals(distanceWinner, AgentCombatGrindTargetPolicy.pickFromBestTargets(targets));
        assertNull(AgentCombatGrindTargetPolicy.pickFromBestTargets(new ArrayList<>()));
    }

    @Test
    void shouldScoreLocalTargetsWithAdjustedLocalScoreAndDistance() {
        Monster near = monsterAt(130, 100);
        Monster far = monsterAt(200, 100);
        Point agentPosition = new Point(100, 100);

        List<AgentScoredGrindTarget> scoredTargets = AgentCombatGrindTargetPolicy.scoreLocalTargets(
                List.of(near, far),
                agentPosition,
                monster -> monster == near ? 150L : 300L,
                monster -> monster == near ? 50L : 0L);

        assertEquals(near, scoredTargets.get(0).monster());
        assertEquals(100L, scoredTargets.get(0).graphCost());
        assertEquals(100L, scoredTargets.get(0).localScore());
        assertEquals(900.0d, scoredTargets.get(0).distanceSq());
        assertEquals(far, scoredTargets.get(1).monster());
        assertEquals(300L, scoredTargets.get(1).graphCost());
        assertEquals(300L, scoredTargets.get(1).localScore());
        assertEquals(10_000.0d, scoredTargets.get(1).distanceSq());
    }

    @Test
    void shouldScoreTargetRegionsByGroupingValidRegions() {
        Monster skipped = monsterAt(50, 100);
        Monster regionOneFar = monsterAt(200, 100);
        Monster regionOneBest = monsterAt(120, 100);
        Monster regionTwo = monsterAt(300, 100);
        Point agentPosition = new Point(100, 100);

        List<AgentScoredGrindTarget> scoredTargets = AgentCombatGrindTargetPolicy.scoreTargetRegions(
                List.of(skipped, regionOneFar, regionOneBest, regionTwo),
                agentPosition,
                monster -> {
                    if (monster == skipped) {
                        return -1;
                    }
                    return monster == regionTwo ? 2 : 1;
                },
                monster -> {
                    if (monster == regionOneBest || monster == regionTwo) {
                        return 100L;
                    }
                    return 120L;
                },
                group -> group.regionId() == 1 ? 1_000L : 500L,
                group -> group.regionId() == 1 ? 25L : 0L,
                9_999L);

        assertEquals(2, scoredTargets.size());
        AgentScoredGrindTarget regionOne = scoredTargets.stream()
                .filter(target -> target.monster() == regionOneBest)
                .findFirst()
                .orElseThrow();
        assertEquals(625L, regionOne.graphCost());
        assertEquals(125L, regionOne.localScore());
        assertEquals(400.0d, regionOne.distanceSq());

        AgentScoredGrindTarget regionTwoScore = scoredTargets.stream()
                .filter(target -> target.monster() == regionTwo)
                .findFirst()
                .orElseThrow();
        assertEquals(500L, regionTwoScore.graphCost());
        assertEquals(100L, regionTwoScore.localScore());
        assertEquals(40_000.0d, regionTwoScore.distanceSq());
    }

    @Test
    void shouldPreserveUnreachableCostWhenScoringTargetRegions() {
        Monster target = monsterAt(120, 100);

        AgentScoredGrindTarget scored = AgentCombatGrindTargetPolicy.scoreTargetRegions(
                List.of(target),
                new Point(100, 100),
                monster -> 1,
                monster -> 100L,
                group -> 9_999L,
                group -> 25L,
                9_999L).get(0);

        assertEquals(9_999L, scored.graphCost());
        assertEquals(125L, scored.localScore());
    }

    @Test
    void shouldReturnEmptyRegionScoresWhenAllTargetsHaveInvalidRegions() {
        Monster target = monsterAt(120, 100);

        assertTrue(AgentCombatGrindTargetPolicy.scoreTargetRegions(
                List.of(target),
                new Point(100, 100),
                monster -> -1,
                monster -> 100L,
                group -> 1L,
                group -> 1L,
                9_999L).isEmpty());
    }

    @Test
    void shouldGroupTargetsByBestLocalScoreThenDistance() {
        Monster farTie = mock(Monster.class);
        Monster localWinner = mock(Monster.class);
        Monster distanceWinner = mock(Monster.class);
        AgentGrindTargetGroup group = new AgentGrindTargetGroup(9);

        group.add(farTie, 100, 500.0);
        group.add(localWinner, 90, 999.0);
        group.add(distanceWinner, 90, 50.0);

        assertEquals(9, group.regionId());
        assertEquals(3, group.mobCount());
        assertEquals(distanceWinner, group.bestMonster());
        assertEquals(90, group.bestLocalScore());
        assertEquals(50.0, group.bestDistanceSq());
    }

    @Test
    void shouldConvertRegionGroupToLegacyScoredTarget() {
        Monster best = mock(Monster.class);
        AgentGrindTargetGroup group = new AgentGrindTargetGroup(4);
        group.add(best, 120, 25.0);
        group.add(mock(Monster.class), 130, 9.0);

        AgentScoredGrindTarget scored = AgentCombatGrindTargetPolicy.toScoredTarget(
                group, 1_000, 50, 9_999);

        assertEquals(best, scored.monster());
        assertEquals(650, scored.graphCost());
        assertEquals(170, scored.localScore());
        assertEquals(25.0, scored.distanceSq());
    }

    @Test
    void shouldCapCrowdBonusAndPreserveUnreachableGraphCost() {
        Monster best = mock(Monster.class);
        AgentGrindTargetGroup group = new AgentGrindTargetGroup(4);
        for (int i = 0; i < 20; i++) {
            group.add(i == 0 ? best : mock(Monster.class), 100 + i, i);
        }

        assertEquals(3_000, AgentCombatGrindTargetPolicy.regionCrowdBonus(group.mobCount()));
        AgentScoredGrindTarget scored = AgentCombatGrindTargetPolicy.toScoredTarget(
                group, 9_999, 50, 9_999);
        assertEquals(9_999, scored.graphCost());
        assertEquals(150, scored.localScore());
    }

    @Test
    void shouldCalculateCappedOccupancyPenalty() {
        assertEquals(0L, AgentCombatGrindTargetPolicy.occupancyPenalty(-1, 100, 500));
        assertEquals(0L, AgentCombatGrindTargetPolicy.occupancyPenalty(3, -100, 500));
        assertEquals(300L, AgentCombatGrindTargetPolicy.occupancyPenalty(3, 100, 500));
        assertEquals(500L, AgentCombatGrindTargetPolicy.occupancyPenalty(9, 100, 500));
        assertEquals(0L, AgentCombatGrindTargetPolicy.occupancyPenalty(9, 100, -1));
    }

    @Test
    void shouldInspectOnlyActiveSiblingOccupantsInSameMap() {
        assertTrue(AgentCombatGrindTargetPolicy.shouldInspectRegionOccupant(
                false, true, true, true, true));
        assertFalse(AgentCombatGrindTargetPolicy.shouldInspectRegionOccupant(
                true, true, true, true, true));
        assertFalse(AgentCombatGrindTargetPolicy.shouldInspectRegionOccupant(
                false, false, true, true, true));
        assertFalse(AgentCombatGrindTargetPolicy.shouldInspectRegionOccupant(
                false, true, false, true, true));
        assertFalse(AgentCombatGrindTargetPolicy.shouldInspectRegionOccupant(
                false, true, true, false, true));
        assertFalse(AgentCombatGrindTargetPolicy.shouldInspectRegionOccupant(
                false, true, true, true, false));
    }

    @Test
    void shouldCountOnlyMatchingTargetRegionOccupants() {
        assertTrue(AgentCombatGrindTargetPolicy.shouldCountRegionOccupant(7, 7));
        assertFalse(AgentCombatGrindTargetPolicy.shouldCountRegionOccupant(7, 8));
        assertFalse(AgentCombatGrindTargetPolicy.shouldCountRegionOccupant(7, -1));
    }

    @Test
    void shouldCalculateGraphPathCostFromLegacyPathInputs() {
        assertEquals(9_999L, AgentCombatGrindTargetPolicy.graphPathCost(
                false, false, 123L, List.of(100L), 9_999L));
        assertEquals(123L, AgentCombatGrindTargetPolicy.graphPathCost(
                true, true, 123L, List.of(), 9_999L));
        assertEquals(9_999L, AgentCombatGrindTargetPolicy.graphPathCost(
                true, false, 123L, List.of(), 9_999L));
        assertEquals(300L, AgentCombatGrindTargetPolicy.graphPathCost(
                true, false, 0L, List.of(100L, 200L), 9_999L));
    }

    private static Foothold foothold(int id) {
        return new Foothold(new Point(0, 0), new Point(100, 0), id);
    }

    private static Monster monsterAt(int x, int y) {
        Monster monster = mock(Monster.class);
        when(monster.getPosition()).thenReturn(new Point(x, y));
        return monster;
    }
}
