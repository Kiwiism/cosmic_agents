package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
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

    private static Foothold foothold(int id) {
        return new Foothold(new Point(0, 0), new Point(100, 0), id);
    }
}
