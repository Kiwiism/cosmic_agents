package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCombatTargetSearchModeStateTest {
    @Test
    void escalatesOnlyAfterRepeatedEmptyLocalScans() {
        AgentCombatTargetSearchModeState state = new AgentCombatTargetSearchModeState();
        state.synchronizeScope(100, "hunt", 1_000);

        assertFalse(state.observeLocalPreferred(false, 3, 1_100));
        assertFalse(state.observeLocalPreferred(false, 3, 1_500));
        assertTrue(state.observeLocalPreferred(false, 3, 1_900));

        state.enter(AgentCombatTargetSearchMode.MAP_WIDE_RECOVERY,
                "local required population exhausted", 44, 1_900);
        AgentCombatTargetSearchModeState.Snapshot snapshot = state.snapshot();
        assertEquals(AgentCombatTargetSearchMode.MAP_WIDE_RECOVERY, snapshot.mode());
        assertEquals(3, snapshot.emptyPreferredScans());
        assertEquals(44, snapshot.destinationRegionId());
    }

    @Test
    void localRequiredPopulationResetsRecoveryAndEvidenceIsBounded() {
        AgentCombatTargetSearchModeState state = new AgentCombatTargetSearchModeState();
        state.synchronizeScope(100, "hunt", 1_000);
        state.observeLocalPreferred(false, 1, 1_100);
        state.enter(AgentCombatTargetSearchMode.MAP_WIDE_RECOVERY, "empty", 44, 1_100);

        assertFalse(state.observeLocalPreferred(true, 3, 1_200));
        state.recordEvidence(8, 3, List.of(
                new AgentCombatTargetSearchModeState.RankedRegion(1, 100, 10, 11, 101),
                new AgentCombatTargetSearchModeState.RankedRegion(2, 200, 20, 12, 102)));

        AgentCombatTargetSearchModeState.Snapshot snapshot = state.snapshot();
        assertEquals(AgentCombatTargetSearchMode.LOCAL_CLEAR, snapshot.mode());
        assertEquals(0, snapshot.emptyPreferredScans());
        assertEquals(2, snapshot.rankedRegions().size());
    }
}
