package server.agents.monitoring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.simulation.AgentSimulationMode;
import server.agents.runtime.scheduler.AgentWorkClass;

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

    @Test
    void recordsBoundedPercentilesPressureAndWorkClassCost() {
        AgentSchedulerMetrics.recordUpdated(1L, 100L, AgentWorkClass.PRESENTATION_GAMEPLAY, false);
        AgentSchedulerMetrics.recordUpdated(5L, 500L, AgentWorkClass.PRESENTATION_GAMEPLAY, false);
        AgentSchedulerMetrics.recordBudgetExhausted();
        AgentSchedulerMetrics.recordDeferred(3L);
        AgentSchedulerMetrics.recordStarvationPromotions(2L);
        AgentSchedulerMetrics.recordMapBudgetDeferral();
        AgentSchedulerMetrics.recordDepths(4, 8, 10, 6);

        AgentSchedulerMetrics.Snapshot snapshot = AgentSchedulerMetrics.snapshot();
        assertEquals(1L, snapshot.queueLagP50Ms());
        assertEquals(5L, snapshot.queueLagP95Ms());
        assertEquals(100L, snapshot.workDurationP50Ns());
        assertEquals(500L, snapshot.workDurationP99Ns());
        assertEquals(1L, snapshot.budgetExhaustions());
        assertEquals(3L, snapshot.deferredWork());
        assertEquals(2L, snapshot.starvationPromotions());
        assertEquals(1L, snapshot.mapBudgetDeferrals());
        assertEquals(4L, snapshot.ingressDepth());
        assertEquals(8L, snapshot.ingressHighWaterMark());
        assertEquals(10L, snapshot.dueHeapDepth());
        assertEquals(6L, snapshot.readyDepth());

        AgentSchedulerMetrics.WorkClassSnapshot workClass =
                AgentSchedulerMetrics.workClassSnapshot(AgentWorkClass.PRESENTATION_GAMEPLAY);
        assertEquals(2, workClass.sampleCount());
        assertEquals(100L, workClass.durationP50Ns());
        assertEquals(500L, workClass.durationP99Ns());
    }

    @Test
    void recordsPerShardDepthAndRegistrationImbalance() {
        AgentSchedulerMetrics.recordShardDepths(0, 12, 2, 8, 2);
        AgentSchedulerMetrics.recordShardDepths(1, 9, 1, 7, 1);

        assertEquals(12, AgentSchedulerMetrics.shardSnapshots().get(0).registrations());
        assertEquals(9, AgentSchedulerMetrics.shardSnapshots().get(1).registrations());
        assertEquals(3, AgentSchedulerMetrics.shardRegistrationImbalance());
        assertEquals(3, AgentSchedulerMetrics.snapshot().ingressDepth());
        assertEquals(3, AgentSchedulerMetrics.snapshot().ingressHighWaterMark());
        assertEquals(15, AgentSchedulerMetrics.snapshot().dueHeapDepth());
        assertEquals(3, AgentSchedulerMetrics.snapshot().readyDepth());
    }

    @Test
    void recordsBoundedWorkDurationBySimulationMode() {
        AgentSchedulerMetrics.recordUpdated(
                1L,
                250L,
                AgentWorkClass.BACKGROUND_GAMEPLAY,
                AgentSimulationMode.BACKGROUND_ACTIVE,
                false);

        AgentSchedulerMetrics.SimulationModeSnapshot snapshot =
                AgentSchedulerMetrics.simulationModeSnapshot(AgentSimulationMode.BACKGROUND_ACTIVE);
        assertEquals(1, snapshot.sampleCount());
        assertEquals(250L, snapshot.durationP50Ns());
        assertEquals(250L, snapshot.durationP99Ns());
    }
}
