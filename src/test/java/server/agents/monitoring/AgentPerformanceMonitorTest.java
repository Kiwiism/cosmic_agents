package server.agents.monitoring;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPerformanceMonitorTest {
    @Test
    void recordsAndResetsSectionSnapshotsWithoutChangingRuntimeSemantics() {
        boolean previouslyEnabled = AgentPerformanceMonitor.enabled();
        try {
            AgentPerformanceMonitor.setEnabled(true);
            AgentPerformanceMonitor.record("test-section", 2_000_000L);
            AgentPerformanceMonitor.record("test-section", 3_000_000L);

            List<AgentPerformanceMonitor.SectionSnapshot> snapshots = AgentPerformanceMonitor.snapshot();

            assertEquals(1, snapshots.size());
            AgentPerformanceMonitor.SectionSnapshot snapshot = snapshots.getFirst();
            assertEquals("test-section", snapshot.section());
            assertEquals(2, snapshot.count());
            assertEquals(5_000_000L, snapshot.totalNs());
            assertEquals(3_000_000L, snapshot.maxNs());

            AgentPerformanceMonitor.reset();
            assertTrue(AgentPerformanceMonitor.snapshot().isEmpty());
        } finally {
            AgentPerformanceMonitor.setEnabled(previouslyEnabled);
        }
    }

    @Test
    void disabledMonitoringRemainsANoOp() {
        boolean previouslyEnabled = AgentPerformanceMonitor.enabled();
        try {
            AgentPerformanceMonitor.setEnabled(false);
            AgentPerformanceMonitor.record("disabled", 1_000_000L);

            assertTrue(AgentPerformanceMonitor.snapshot().isEmpty());
        } finally {
            AgentPerformanceMonitor.setEnabled(previouslyEnabled);
        }
    }
}
