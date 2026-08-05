package server.agents.capabilities.looting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AgentLootDecisionTraceStateTest {
    @Test
    void recordsLootPolicyEvidenceWithoutChangingIt() {
        AgentLootDecisionTraceState state = new AgentLootDecisionTraceState();

        state.record(
                AgentLootDecisionTraceState.Mode.POST_KILL_MELEE,
                AgentLootDecisionTraceState.Outcome.WAITING_FOR_DROP,
                2_000L,
                2,
                true,
                81,
                1_000L,
                650L);

        AgentLootDecisionTraceState.Snapshot snapshot = state.snapshot();
        assertEquals(AgentLootDecisionTraceState.Mode.POST_KILL_MELEE, snapshot.mode());
        assertEquals(AgentLootDecisionTraceState.Outcome.WAITING_FOR_DROP, snapshot.outcome());
        assertEquals(2_000L, snapshot.recordedAtMs());
        assertEquals(2, snapshot.recentKillCount());
        assertTrue(snapshot.combatTargetPresent());
        assertEquals(81, snapshot.targetObjectId());
        assertEquals(1_000L, snapshot.requiredDropAgeMs());
        assertEquals(650L, snapshot.observedDropAgeMs());
    }

    @Test
    void clampsInvalidDiagnosticValues() {
        AgentLootDecisionTraceState state = new AgentLootDecisionTraceState();

        state.record(null, null, 0L, -1, false, -2, -3L, -4L);

        AgentLootDecisionTraceState.Snapshot snapshot = state.snapshot();
        assertEquals(AgentLootDecisionTraceState.Mode.NONE, snapshot.mode());
        assertEquals(AgentLootDecisionTraceState.Outcome.NONE, snapshot.outcome());
        assertEquals(0, snapshot.recentKillCount());
        assertEquals(0, snapshot.targetObjectId());
        assertEquals(0L, snapshot.requiredDropAgeMs());
        assertEquals(0L, snapshot.observedDropAgeMs());
    }
}
