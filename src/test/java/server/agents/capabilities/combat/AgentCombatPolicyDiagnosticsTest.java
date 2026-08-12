package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentCombatPolicyDiagnosticsTest {
    @Test
    void snapshotExposesCombatLocalLeaseWithoutOwningNavigationDiagnostics() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentCombatLocalTargetLeaseState lease = entry.capabilityStates()
                .require(AgentCombatLocalTargetLeaseState.STATE_KEY);
        lease.beginMapWideTravel(100, "quest", 44, 1_000, 25_000);
        lease.observeRegion(100, "quest", 44, 2_000, 25_000, 3);

        AgentCombatPolicyDiagnostics.Snapshot snapshot =
                AgentCombatPolicyDiagnostics.snapshot(entry, 2_200);

        assertNotNull(snapshot.localTargetLease());
        assertEquals(AgentCombatLocalTargetLeaseState.Phase.ACTIVE,
                snapshot.localTargetLease().phase());
        assertEquals(3, snapshot.localTargetLease().killsRemaining());
    }
}
