package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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

    private static Foothold foothold(int id) {
        return new Foothold(new Point(0, 0), new Point(100, 0), id);
    }
}
