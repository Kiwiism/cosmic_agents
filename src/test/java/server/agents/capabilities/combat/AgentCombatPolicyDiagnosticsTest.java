package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.navigation.AgentNavigationEdgeReliabilityState;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentCombatPolicyDiagnosticsTest {
    @Test
    void snapshotExposesLocalLeaseAndNavigationReliabilityLedger() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentCombatLocalTargetLeaseState lease = entry.capabilityStates()
                .require(AgentCombatLocalTargetLeaseState.STATE_KEY);
        lease.beginMapWideTravel(100, "quest", 44, 1_000, 25_000);
        lease.observeRegion(100, "quest", 44, 2_000, 25_000, 3);

        AgentNavigationGraph.Edge edge = new AgentNavigationGraph.Edge(
                1, 2, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(10, 100), new Point(10, 50),
                10, 10, 0, -1, 10, 50, 100, 500);
        AgentNavigationEdgeReliabilityState reliability = entry.capabilityStates()
                .require(AgentNavigationEdgeReliabilityState.STATE_KEY);
        reliability.recordFailure(100, edge, 2_100, 3, 30_000, 60_000, 32);
        reliability.recordFailure(100, edge, 2_101, 3, 30_000, 60_000, 32);
        reliability.recordFailure(100, edge, 2_102, 3, 30_000, 60_000, 32);

        AgentCombatPolicyDiagnostics.Snapshot snapshot =
                AgentCombatPolicyDiagnostics.snapshot(entry, 2_200);

        assertNotNull(snapshot.localTargetLease());
        assertEquals(AgentCombatLocalTargetLeaseState.Phase.ACTIVE,
                snapshot.localTargetLease().phase());
        assertEquals(3, snapshot.localTargetLease().killsRemaining());
        assertEquals(1, snapshot.navigationReliability().trackedEdgeCount());
        assertEquals(3,
                snapshot.navigationReliability().edges().getFirst().failureCount());
        assertEquals(6_000,
                snapshot.navigationReliability().edges().getFirst().penaltyMs());
        assertEquals(32_102,
                snapshot.navigationReliability().edges().getFirst().suppressedUntilMs());
    }
}
