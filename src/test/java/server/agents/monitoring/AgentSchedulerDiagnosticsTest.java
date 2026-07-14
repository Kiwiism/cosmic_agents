package server.agents.monitoring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentTickSliceKind;
import server.agents.runtime.scheduler.AgentLoadSheddingLevel;
import server.agents.runtime.scheduler.AgentLoadSheddingReason;
import server.agents.runtime.scheduler.AgentLoadSheddingState;
import server.agents.runtime.scheduler.AgentPriorityClass;
import server.agents.runtime.scheduler.AgentSchedulerMode;
import server.agents.runtime.scheduler.AgentSessionId;
import server.agents.runtime.scheduler.AgentWorkClass;
import server.agents.runtime.simulation.AgentSimulationMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        AgentSchedulerMetrics.recordShardDepths(
                0, 12, 2, 8, 1, Map.of(AgentPriorityClass.VISIBLE, 1));
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
        List<String> lines = AgentSchedulerDiagnostics.lines();

        assertEquals("CENTRAL_SHARDED", snapshot.mode().name());
        assertEquals(2, snapshot.shards().size());
        assertEquals(2, snapshot.shardRegistrationImbalance());
        assertEquals(3, snapshot.asyncQueues().get("navigation").currentDepth());
        assertTrue(lines.stream().anyMatch(line -> line.contains("mode=CENTRAL_SHARDED")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("shard=0 agents=12")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("level=SUPPRESS_COSMETIC")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("queue=navigation depth=3/64")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Scheduler registrations:")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Scheduler cycle budget:")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Scheduler lifecycle:")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Scheduler ready queues: VISIBLE=1/1")));
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
        assertTrue(lines.size() <= 34);
    }

    @Test
    void detailViewsRankAndBoundLiveSchedulerState() {
        List<AgentSchedulerDetailDiagnostics.AgentView> agents = new ArrayList<>();
        List<AgentSchedulerRegistrationSnapshot> registrations = new ArrayList<>();
        for (int id = 1; id <= 12; id++) {
            agents.add(new AgentSchedulerDetailDiagnostics.AgentView(
                    id,
                    id,
                    "agent" + id,
                    id <= 7 ? 100 : 200,
                    id,
                    id == 12 ? 2 : 0,
                    AgentSchedulerMode.CENTRAL_SEQUENTIAL));
            registrations.add(new AgentSchedulerRegistrationSnapshot(
                    new AgentSessionId(id, id),
                    1_000L - id,
                    id * 1_000L,
                    AgentWorkClass.PRESENTATION_GAMEPLAY,
                    AgentPriorityClass.VISIBLE,
                    AgentSimulationMode.PRESENTATION,
                    true,
                    false,
                    false));
        }

        List<String> slow = AgentSchedulerDetailDiagnostics.top(
                new String[] {"top", "slow"}, agents, registrations, List.of(), 2_000L);
        List<String> overdue = AgentSchedulerDetailDiagnostics.top(
                new String[] {"top", "overdue"}, agents, registrations, List.of(), 2_000L);
        List<String> maps = AgentSchedulerDetailDiagnostics.top(
                new String[] {"top", "maps"}, agents, registrations, List.of(), 2_000L);
        List<String> mailboxes = AgentSchedulerDetailDiagnostics.top(
                new String[] {"top", "mailboxes"}, agents, registrations, List.of(), 2_000L);
        List<String> failures = AgentSchedulerDetailDiagnostics.top(
                new String[] {"top", "failures"}, agents, registrations, List.of(), 2_000L);

        assertEquals(11, slow.size());
        assertTrue(slow.get(1).contains("agent12"));
        assertEquals(11, overdue.size());
        assertTrue(overdue.get(1).contains("agent12"));
        assertTrue(maps.get(1).contains("map=100 agents=7"));
        assertEquals(11, mailboxes.size());
        assertTrue(mailboxes.get(1).contains("agent12"));
        assertEquals(2, failures.size());
        assertTrue(failures.get(1).contains("agent12"));
    }

    @Test
    void capabilityViewUsesExistingBoundedPerformanceSamples() {
        List<AgentPerformanceMonitor.SectionSnapshot> sections = List.of(
                new AgentPerformanceMonitor.SectionSnapshot("movement", 4, 20_000_000L, 8_000_000L, 0, 0),
                new AgentPerformanceMonitor.SectionSnapshot("combat", 2, 30_000_000L, 20_000_000L, 0, 0));

        List<String> lines = AgentSchedulerDetailDiagnostics.top(
                new String[] {"top", "capabilities"}, List.of(), List.of(), sections, 0L);

        assertEquals(3, lines.size());
        assertTrue(lines.get(1).contains("section=combat"));
        assertTrue(lines.get(2).contains("section=movement"));
    }

    @Test
    void detailCommandsReturnBoundedEmptyStateMessages() {
        assertEquals(
                List.of("No active Agent mailbox contains queued work."),
                AgentSchedulerDetailDiagnostics.top(
                        new String[] {"top", "mailboxes"}, List.of(), List.of(), List.of(), 0L));
        assertEquals(
                List.of("No central Agent registration is currently overdue."),
                AgentSchedulerDetailDiagnostics.top(
                        new String[] {"top", "overdue"}, List.of(), List.of(), List.of(), 0L));
    }

    @Test
    void costViewReportsWorkSimulationAndTickSlicePercentiles() {
        AgentSchedulerMetrics.recordUpdated(
                2L,
                4_000L,
                AgentWorkClass.PRESENTATION_GAMEPLAY,
                AgentSimulationMode.PRESENTATION,
                false);
        AgentSchedulerMetrics.recordTickSlice(AgentTickSliceKind.CAPABILITY_AND_MOVEMENT, 6_000L);

        List<String> lines = AgentSchedulerDiagnostics.lines(new String[] {"costs"});

        assertTrue(lines.stream().anyMatch(line -> line.contains("work=PRESENTATION_GAMEPLAY")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("mode=PRESENTATION")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("slice=CAPABILITY_AND_MOVEMENT")));
    }
}
