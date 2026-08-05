package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AgentCombatDecisionTraceStateTest {
    @Test
    void recordsPolicyLayerEvidenceWithoutChangingIt() {
        AgentCombatDecisionTraceState state = new AgentCombatDecisionTraceState();

        state.record(
                AgentCombatDecisionTraceState.Mode.GRIND,
                AgentCombatDecisionTraceState.Outcome.SELECTED,
                1_234L,
                12,
                10,
                8,
                6,
                4,
                true,
                true,
                44,
                100_100);

        AgentCombatDecisionTraceState.Snapshot snapshot = state.snapshot();
        assertEquals(AgentCombatDecisionTraceState.Mode.GRIND, snapshot.mode());
        assertEquals(AgentCombatDecisionTraceState.Outcome.SELECTED, snapshot.outcome());
        assertEquals(1_234L, snapshot.recordedAtMs());
        assertEquals(12, snapshot.baseCandidates());
        assertEquals(10, snapshot.objectiveCandidates());
        assertEquals(8, snapshot.policyCandidates());
        assertEquals(6, snapshot.claimCandidates());
        assertEquals(4, snapshot.scoredCandidates());
        assertTrue(snapshot.mapWidePreferredEscalation());
        assertTrue(snapshot.rankedVariationConsumed());
        assertEquals(44, snapshot.selectedObjectId());
        assertEquals(100_100, snapshot.selectedMobId());
    }

    @Test
    void clampsInvalidDiagnosticCounts() {
        AgentCombatDecisionTraceState state = new AgentCombatDecisionTraceState();

        state.record(null, null, 0L, -1, -2, -3, -4, -5,
                false, false, -6, -7);

        AgentCombatDecisionTraceState.Snapshot snapshot = state.snapshot();
        assertEquals(AgentCombatDecisionTraceState.Mode.NONE, snapshot.mode());
        assertEquals(AgentCombatDecisionTraceState.Outcome.NONE, snapshot.outcome());
        assertEquals(0, snapshot.baseCandidates());
        assertEquals(0, snapshot.selectedObjectId());
        assertEquals(0, snapshot.selectedMobId());
    }
}
