package server.agents.monitoring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.scheduler.AgentLoadSheddingLevel;
import server.agents.runtime.scheduler.AgentLoadSheddingReason;
import server.agents.runtime.scheduler.AgentLoadSheddingState;
import server.agents.runtime.scheduler.AgentWorkClass;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSchedulerDiagnosticsTest {
    @AfterEach
    void tearDown() {
        System.clearProperty("agents.scheduler.mode");
        AgentSchedulerMetrics.reset();
        AgentAsyncQueueMetrics.reset();
    }

    @Test
    void capturesCurrentSchedulerShardPressureAndQueueMetrics() {
        System.setProperty("agents.scheduler.mode", "central-sharded");
        AgentSchedulerMetrics.recordCycle(2_500_000L);
        AgentSchedulerMetrics.recordUpdated(4L, 9_000L, AgentWorkClass.PRESENTATION_GAMEPLAY, false);
        AgentSchedulerMetrics.recordShardDepths(0, 12, 2, 8, 1);
        AgentSchedulerMetrics.recordShardDepths(1, 10, 1, 7, 0);
        AgentSchedulerMetrics.recordLoadSheddingTransition(
                1,
                AgentLoadSheddingLevel.NORMAL,
                new AgentLoadSheddingState(
                        AgentLoadSheddingLevel.SUPPRESS_COSMETIC,
                        Set.of(AgentLoadSheddingReason.QUEUE_LAG),
                        1L,
                        1L));
        AgentSchedulerMetrics.recordQuiescenceRequested();
        AgentSchedulerMetrics.recordQuiescenceCompleted(25L);
        AgentAsyncQueueMetrics.recordCapacity("navigation", 64);
        AgentAsyncQueueMetrics.recordSubmitted("navigation", 3);

        AgentSchedulerDiagnostics.Snapshot snapshot = AgentSchedulerDiagnostics.capture();
        List<String> lines = AgentSchedulerDiagnostics.format(snapshot);

        assertEquals("CENTRAL_SHARDED", snapshot.mode().name());
        assertEquals(2, snapshot.shards().size());
        assertEquals(2, snapshot.shardRegistrationImbalance());
        assertEquals(3, snapshot.asyncQueues().get("navigation").currentDepth());
        assertTrue(lines.stream().anyMatch(line -> line.contains("mode=CENTRAL_SHARDED")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("shard=0 agents=12")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("level=SUPPRESS_COSMETIC")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("queue=navigation depth=3/64")));
    }

    @Test
    void boundsShardAndAsyncQueueDetail() {
        System.setProperty("agents.scheduler.mode", "central-sharded");
        for (int shard = 0; shard < 12; shard++) {
            AgentSchedulerMetrics.recordShardDepths(shard, shard, 0, 0, 0);
        }
        for (int queue = 0; queue < 16; queue++) {
            AgentAsyncQueueMetrics.recordDepth("queue-" + queue, queue);
        }

        List<String> lines = AgentSchedulerDiagnostics.lines();

        assertTrue(lines.stream().anyMatch(line -> line.contains("4 more shard(s)")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("4 more queue(s)")));
        assertTrue(lines.size() <= 29);
    }
}
