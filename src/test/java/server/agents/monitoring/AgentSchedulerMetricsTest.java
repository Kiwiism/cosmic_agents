package server.agents.monitoring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentSchedulerMetricsTest {
    @AfterEach
    void tearDown() {
        AgentSchedulerMetrics.reset();
    }

    @Test
    void recordsCycleAgentLagSkipFailureAndSlowMetrics() {
        AgentSchedulerMetrics.recordCycle(25L);
        AgentSchedulerMetrics.recordUpdated(4L, true);
        AgentSchedulerMetrics.recordSkipped(2L);
        AgentSchedulerMetrics.recordFailure();

        AgentSchedulerMetrics.Snapshot snapshot = AgentSchedulerMetrics.snapshot();
        assertEquals(1, snapshot.cycles());
        assertEquals(1, snapshot.updatedAgents());
        assertEquals(2, snapshot.skippedAgents());
        assertEquals(1, snapshot.failedAgents());
        assertEquals(1, snapshot.slowAgents());
        assertEquals(25, snapshot.totalCycleNs());
        assertEquals(25, snapshot.maxCycleNs());
        assertEquals(4, snapshot.totalQueueLagMs());
        assertEquals(4, snapshot.maxQueueLagMs());
    }
}
