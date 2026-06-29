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

    private static Foothold foothold(int id) {
        return new Foothold(new Point(0, 0), new Point(100, 0), id);
    }
}
