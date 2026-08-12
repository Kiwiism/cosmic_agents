package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCombatLocalTargetLeaseStateTest {
    @Test
    void mapWideTravelActivatesOnArrivalAndKeepsLocalObjectivePriority() {
        AgentCombatLocalTargetLeaseState state = new AgentCombatLocalTargetLeaseState();

        state.beginMapWideTravel(100, "orange-mushrooms", 44, 1_000, 25_000);
        assertEquals(AgentCombatLocalTargetLeaseState.Phase.TRAVELLING,
                state.snapshot(1_000).phase());
        assertFalse(state.scan(false, 1_000, 3),
                "the selected map-wide area remains authoritative while travelling");

        state.observeRegion(100, "orange-mushrooms", 44, 2_000, 25_000, 3);

        assertEquals(AgentCombatLocalTargetLeaseState.Phase.ACTIVE,
                state.snapshot(2_000).phase());
        assertFalse(state.scan(true, 2_100, 3));
        assertFalse(state.scan(true, 2_200, 3));
        assertEquals(0, state.snapshot(2_200).emptyScans());
    }

    @Test
    void killsAndConsecutiveEmptyScansReleaseAtConfiguredBounds() {
        AgentCombatLocalTargetLeaseState state = activeState(3);

        state.recordLocalKill(100, "quest", 2_100);
        assertEquals(2, state.snapshot(2_100).killsRemaining());
        state.recordLocalKill(999, "stale-objective", 2_150);
        assertEquals(2, state.snapshot(2_150).killsRemaining(),
                "stale kill events must not reset or decrement a new map/objective lease");
        assertFalse(state.scan(false, 2_200, 3));
        assertFalse(state.scan(false, 2_300, 3));
        assertTrue(state.scan(false, 2_400, 3));
        assertEquals(AgentCombatLocalTargetLeaseState.Phase.INACTIVE,
                state.snapshot(2_400).phase());

        state = activeState(2);
        state.recordLocalKill(100, "quest", 2_100);
        state.recordLocalKill(100, "quest", 2_200);
        assertEquals(AgentCombatLocalTargetLeaseState.Phase.INACTIVE,
                state.snapshot(2_200).phase());
    }

    @Test
    void localCandidateResetsEmptyScanAndScopeChangesResetLease() {
        AgentCombatLocalTargetLeaseState state = activeState(3);
        assertFalse(state.scan(false, 2_100, 3));
        assertFalse(state.scan(true, 2_200, 3));
        assertEquals(0, state.snapshot(2_200).emptyScans());

        state.synchronizeScope(101, "quest");
        assertEquals(AgentCombatLocalTargetLeaseState.Phase.INACTIVE,
                state.snapshot(2_300).phase());
        state.beginMapWideTravel(101, "quest", 50, 2_300, 25_000);
        state.synchronizeScope(101, "different-objective");
        assertEquals(AgentCombatLocalTargetLeaseState.Phase.INACTIVE,
                state.snapshot(2_400).phase());
    }

    @Test
    void timeExpiryAllowsAnotherMapWideEscape() {
        AgentCombatLocalTargetLeaseState state = activeState(3);

        assertTrue(state.scan(false, 27_001, 3));
        assertEquals(AgentCombatLocalTargetLeaseState.Phase.INACTIVE,
                state.snapshot(27_001).phase());
    }

    @Test
    void cancelledTravelAllowsReplacementForDespawnedRemoteTarget() {
        AgentCombatLocalTargetLeaseState state = new AgentCombatLocalTargetLeaseState();
        state.beginMapWideTravel(100, "quest", 44, 1_000, 25_000);

        state.cancelTravel();

        assertTrue(state.scan(false, 1_100, 3));
        assertEquals(AgentCombatLocalTargetLeaseState.Phase.INACTIVE,
                state.snapshot(1_100).phase());
    }

    private static AgentCombatLocalTargetLeaseState activeState(int kills) {
        AgentCombatLocalTargetLeaseState state = new AgentCombatLocalTargetLeaseState();
        state.beginMapWideTravel(100, "quest", 44, 1_000, 25_000);
        state.observeRegion(100, "quest", 44, 2_000, 25_000, kills);
        return state;
    }
}
