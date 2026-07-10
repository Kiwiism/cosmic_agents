package server.agents.monitoring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentAsyncQueueMetricsTest {
    @AfterEach
    void tearDown() {
        AgentAsyncQueueMetrics.reset();
    }

    @Test
    void exposesSubmissionSaturationAndDepthCounters() {
        AgentAsyncQueueMetrics.recordSubmitted("test", 1);
        AgentAsyncQueueMetrics.recordSubmitted("test", 3);
        AgentAsyncQueueMetrics.recordCoalesced("test", 2);
        AgentAsyncQueueMetrics.recordRejected("test", 3);
        AgentAsyncQueueMetrics.recordDepth("test", 0);

        AgentAsyncQueueMetrics.Snapshot snapshot = AgentAsyncQueueMetrics.snapshot("test");
        assertEquals(2, snapshot.submitted());
        assertEquals(1, snapshot.rejected());
        assertEquals(1, snapshot.coalesced());
        assertEquals(0, snapshot.currentDepth());
        assertEquals(3, snapshot.maxDepth());
    }
}
