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
        AgentAsyncQueueMetrics.recordCapacity("test", 8);
        AgentAsyncQueueMetrics.recordSubmitted("test", 1);
        AgentAsyncQueueMetrics.recordSubmitted("test", 3);
        AgentAsyncQueueMetrics.recordCoalesced("test", 2);
        AgentAsyncQueueMetrics.recordRejected("test", 3);
        AgentAsyncQueueMetrics.recordWorkerStarted("test", 2);
        AgentAsyncQueueMetrics.recordCompleted("test", 10L);
        AgentAsyncQueueMetrics.recordFailed("test", 20L);
        AgentAsyncQueueMetrics.recordTimedOut("test", 30L);
        AgentAsyncQueueMetrics.recordStale("test");
        AgentAsyncQueueMetrics.recordExpired("test");
        AgentAsyncQueueMetrics.recordDrained("test", 4L);
        AgentAsyncQueueMetrics.recordWorkerStopped("test", 0);
        AgentAsyncQueueMetrics.recordDepth("test", 0);

        AgentAsyncQueueMetrics.Snapshot snapshot = AgentAsyncQueueMetrics.snapshot("test");
        assertEquals(2, snapshot.submitted());
        assertEquals(1, snapshot.rejected());
        assertEquals(1, snapshot.coalesced());
        assertEquals(0, snapshot.currentDepth());
        assertEquals(3, snapshot.maxDepth());
        assertEquals(8, snapshot.capacity());
        assertEquals(0, snapshot.active());
        assertEquals(3, snapshot.completed());
        assertEquals(1, snapshot.failed());
        assertEquals(1, snapshot.timedOut());
        assertEquals(1, snapshot.stale());
        assertEquals(1, snapshot.expired());
        assertEquals(4, snapshot.drained());
        assertEquals(20L, snapshot.durationP50Ns());
        assertEquals(30L, snapshot.durationP95Ns());
        assertEquals(30L, snapshot.durationP99Ns());
        assertEquals(3, snapshot.durationSamples());
    }
}
