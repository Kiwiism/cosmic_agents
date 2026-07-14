package server.agents.monitoring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentTickSliceKind;
import server.agents.runtime.scheduler.AgentLoadSheddingLevel;
import server.agents.runtime.scheduler.AgentLoadSheddingReason;
import server.agents.runtime.scheduler.AgentLoadSheddingState;
import server.agents.runtime.scheduler.AgentPriorityClass;
import server.agents.runtime.scheduler.AgentWorkClass;
import server.agents.runtime.simulation.AgentSimulationMode;

import java.util.Map;
import java.util.Set;

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
        AgentSchedulerMetrics.recordShardDepths(
                0, 12, 2, 8, 2, Map.of(AgentPriorityClass.VISIBLE, 2));
        AgentSchedulerMetrics.recordShardDepths(
                1, 9, 1, 7, 1, Map.of(AgentPriorityClass.VISIBLE, 1));

        assertEquals(12, AgentSchedulerMetrics.shardSnapshots().get(0).registrations());
        assertEquals(9, AgentSchedulerMetrics.shardSnapshots().get(1).registrations());
        assertEquals(3, AgentSchedulerMetrics.shardRegistrationImbalance());
        assertEquals(3, AgentSchedulerMetrics.snapshot().ingressDepth());
        assertEquals(3, AgentSchedulerMetrics.snapshot().ingressHighWaterMark());
        assertEquals(15, AgentSchedulerMetrics.snapshot().dueHeapDepth());
        assertEquals(3, AgentSchedulerMetrics.snapshot().readyDepth());
        assertEquals(2L, AgentSchedulerMetrics.shardSnapshots().get(0).readyPriorities()
                .get(AgentPriorityClass.VISIBLE).readyDepth());
        assertEquals(3L, AgentSchedulerMetrics.readyPrioritySnapshots()
                .get(AgentPriorityClass.VISIBLE).readyDepth());
        assertEquals(3L, AgentSchedulerMetrics.readyPrioritySnapshots()
                .get(AgentPriorityClass.VISIBLE).readyHighWaterMark());
    }

    @Test
    void retainsReadyPriorityHighWaterAfterDepthFalls() {
        AgentSchedulerMetrics.recordDepths(
                0, 0, 0, 4, Map.of(AgentPriorityClass.INTERACTIVE, 4));
        AgentSchedulerMetrics.recordDepths(
                0, 0, 0, 1, Map.of(AgentPriorityClass.INTERACTIVE, 1));

        AgentSchedulerMetrics.PrioritySnapshot snapshot = AgentSchedulerMetrics.readyPrioritySnapshots()
                .get(AgentPriorityClass.INTERACTIVE);
        assertEquals(1L, snapshot.readyDepth());
        assertEquals(4L, snapshot.readyHighWaterMark());
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

    @Test
    void recordsBoundedTickSliceDurationAndContinuationCount() {
        AgentSchedulerMetrics.recordTickSlice(AgentTickSliceKind.PREFLIGHT, 125L);
        AgentSchedulerMetrics.recordTickContinuation();

        AgentSchedulerMetrics.TickSliceSnapshot slice =
                AgentSchedulerMetrics.tickSliceSnapshot(AgentTickSliceKind.PREFLIGHT);
        assertEquals(1, slice.sampleCount());
        assertEquals(125L, slice.durationP50Ns());
        assertEquals(125L, slice.durationP99Ns());
        assertEquals(1L, AgentSchedulerMetrics.snapshot().tickContinuations());
    }

    @Test
    void recordsReasonCodedLoadSheddingAndAdmissionMetrics() {
        AgentLoadSheddingState state = new AgentLoadSheddingState(
                AgentLoadSheddingLevel.PAUSE_LOW_PRIORITY_BACKGROUND,
                Set.of(AgentLoadSheddingReason.READY_BACKLOG),
                10L,
                1L);
        AgentSchedulerMetrics.recordLoadSheddingTransition(
                2,
                AgentLoadSheddingLevel.NORMAL,
                state);
        AgentSchedulerMetrics.recordLoadSheddingSuppressed(AgentLoadSheddingReason.READY_BACKLOG);
        AgentSchedulerMetrics.recordAgentAdmissionRejected(AgentLoadSheddingReason.POPULATION_LIMIT);

        AgentSchedulerMetrics.LoadSheddingSnapshot snapshot = AgentSchedulerMetrics.loadSheddingSnapshot();
        assertEquals(state, snapshot.shardStates().get(2));
        assertEquals(1L, snapshot.transitions());
        assertEquals(1L, snapshot.suppressedWork());
        assertEquals(1L, snapshot.rejectedAdmissions());
        assertEquals(1L, snapshot.suppressedByReason().get(AgentLoadSheddingReason.READY_BACKLOG));
        assertEquals(1L, snapshot.rejectedAdmissionsByReason().get(AgentLoadSheddingReason.POPULATION_LIMIT));
    }

    @Test
    void recordsBoundedQuiescenceLifecycleAndDurationMetrics() {
        AgentSchedulerMetrics.recordQuiescenceRequested();
        AgentSchedulerMetrics.recordQuiescenceCompleted(25L);
        AgentSchedulerMetrics.recordQuiescenceTimedOut();
        AgentSchedulerMetrics.recordQuiescenceCancelled();
        AgentSchedulerMetrics.recordQuiescenceResumed();

        AgentSchedulerMetrics.QuiescenceSnapshot snapshot = AgentSchedulerMetrics.quiescenceSnapshot();
        assertEquals(1L, snapshot.requested());
        assertEquals(1L, snapshot.completed());
        assertEquals(1L, snapshot.timedOut());
        assertEquals(1L, snapshot.cancelled());
        assertEquals(1L, snapshot.resumed());
        assertEquals(25L, snapshot.durationP50Ms());
        assertEquals(25L, snapshot.durationP99Ms());
        assertEquals(1, snapshot.sampleCount());
    }

    @Test
    void recordsSchedulerRegistrationAndCleanupLifecycle() {
        AgentSchedulerMetrics.recordLifecycleRegistered();
        AgentSchedulerMetrics.recordLifecycleReplaced(2L);
        AgentSchedulerMetrics.recordLifecycleCancellationRequested();
        AgentSchedulerMetrics.recordLifecycleCleanedUp();

        AgentSchedulerMetrics.LifecycleSnapshot snapshot = AgentSchedulerMetrics.lifecycleSnapshot();
        assertEquals(1L, snapshot.registered());
        assertEquals(2L, snapshot.replaced());
        assertEquals(1L, snapshot.cancellationRequests());
        assertEquals(1L, snapshot.cleanedUp());
    }
}
