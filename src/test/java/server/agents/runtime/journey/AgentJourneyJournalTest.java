package server.agents.runtime.journey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.runtime.activity.session.AgentActivityHandoffCoordinator;
import server.agents.runtime.activity.session.AgentActivityHandoffJourneyRecorder;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivityPhase;
import server.agents.runtime.activity.session.AgentActivityTerminalJourneyRecorder;
import server.agents.runtime.activity.session.AgentActivityTerminalOutcome;
import server.agents.runtime.activity.outcome.AgentActivityOutcomeEnvelope;
import server.agents.runtime.decision.AgentDecisionAssessment;
import server.agents.runtime.decision.AgentDecisionJourneyRecorder;
import server.agents.runtime.decision.AgentDecisionReasonCode;
import server.agents.runtime.decision.AgentDecisionRecommendation;
import server.agents.runtime.decision.AgentDecisionSignal;
import server.agents.runtime.decision.AgentDecisionSignalKind;
import server.agents.runtime.decision.AgentRecommendedAction;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentJourneyJournalTest {
    @TempDir
    Path directory;

    @Test
    void persistsOrderedDecisionAndHandoffEvidenceAcrossRestart() {
        AgentFileJourneyJournalStore store = new AgentFileJourneyJournalStore(directory);
        AgentDecisionAssessment assessment = new AgentDecisionAssessment(
                "101", AgentActivityKind.QUESTING, 100L, "decision-1",
                List.of(AgentDecisionSignal.observed(
                        AgentDecisionSignalKind.NAVIGATION_BLOCKED, 100L,
                        "quest-watchdog", "quest:2018", "route failed")));
        AgentDecisionRecommendation recommendation = AgentDecisionRecommendation.from(
                assessment, AgentRecommendedAction.REPLAN_CURRENT,
                AgentDecisionReasonCode.NAVIGATION_BLOCKED,
                "quest-struggle-fallback", "v1", "replan the current quest route");
        new AgentDecisionJourneyRecorder(store).record(101, assessment, recommendation);

        AgentActivityHandoffCoordinator.Handoff handoff = new AgentActivityHandoffCoordinator.Handoff(
                "handoff-1", "world-director", "101",
                AgentActivityKind.QUESTING, AgentActivityKind.TOWN_LIFE,
                "quest-session-1", AgentActivityHandoffCoordinator.Phase.FAILED,
                100L, 1_000L, 200L, 0L, true, "target admission failed");
        new AgentActivityHandoffJourneyRecorder(store).record(101, handoff);

        List<AgentJourneyEvent> restored =
                new AgentFileJourneyJournalStore(directory).read("101");

        assertEquals(List.of(1L, 2L), restored.stream()
                .map(AgentJourneyEvent::sequence).toList());
        assertEquals(AgentJourneyEventType.RECOVERY_RECOMMENDED, restored.getFirst().type());
        assertEquals("true", restored.get(1).evidence().get("requiresSafeFallback"));
    }

    @Test
    void duplicateEventIsIdempotentButConflictingReuseIsRejected() {
        AgentFileJourneyJournalStore store = new AgentFileJourneyJournalStore(directory);
        AgentJourneyEventDraft draft = new AgentJourneyEventDraft(
                "event:1", "agent/1", 101, 100L,
                AgentJourneyEventType.ACTIVITY_TERMINAL, AgentActivityKind.HUNTING,
                "activity-outcome", "hunt-1", "complete", Map.of("kills", "10"));

        AgentJourneyEvent first = store.append(draft);
        AgentJourneyEvent same = store.append(draft);

        assertEquals(first, same);
        assertEquals(1, store.read("agent/1").size());
        assertThrows(IllegalStateException.class, () -> store.append(
                new AgentJourneyEventDraft(
                        "event:1", "agent/1", 101, 101L,
                        AgentJourneyEventType.ACTIVITY_TERMINAL, AgentActivityKind.HUNTING,
                        "activity-outcome", "hunt-1", "different", Map.of())));
    }

    @Test
    void terminalActivityOutcomeIsRecordedOnceWithStructuredEvidence() {
        AgentFileJourneyJournalStore store = new AgentFileJourneyJournalStore(directory);
        AgentActivityTerminalOutcome outcome = new AgentActivityTerminalOutcome(
                AgentActivityKind.HUNTING, AgentActivityPhase.COMPLETED,
                "hunt-session-1", "101", "objective complete", false,
                100L, 200L, Map.of("kills", 12, "mapId", 100000001));
        AgentActivityOutcomeEnvelope envelope = AgentActivityOutcomeEnvelope.published(
                "hunting:hunt-session-1:completed", outcome, 200L);
        AgentActivityTerminalJourneyRecorder recorder =
                new AgentActivityTerminalJourneyRecorder(store);

        recorder.record(envelope);
        recorder.record(envelope);

        List<AgentJourneyEvent> events = store.read("101");
        assertEquals(1, events.size());
        assertEquals(AgentJourneyEventType.ACTIVITY_TERMINAL, events.getFirst().type());
        assertEquals("12", events.getFirst().evidence().get("kills"));
        assertEquals("200", events.getFirst().evidence().get("endedAtMs"));
    }

    @Test
    void coordinatesSequenceAllocationAcrossStoreInstances() throws Exception {
        int eventCount = 32;
        CountDownLatch ready = new CountDownLatch(eventCount);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(eventCount)) {
            var futures = java.util.stream.IntStream.range(0, eventCount)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return new AgentFileJourneyJournalStore(directory).append(
                                new AgentJourneyEventDraft(
                                        "event:" + index, "concurrent-agent", 101,
                                        100L + index, AgentJourneyEventType.ACTIVITY_TERMINAL,
                                        AgentActivityKind.QUESTING, "concurrency-test",
                                        "quest-" + index, "complete", Map.of()));
                    })).toList();
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            for (var future : futures) future.get(10, TimeUnit.SECONDS);
        }

        List<AgentJourneyEvent> events =
                new AgentFileJourneyJournalStore(directory).read("concurrent-agent");
        assertEquals(eventCount, events.size());
        assertEquals(eventCount, events.stream().map(AgentJourneyEvent::sequence)
                .distinct().count());
    }
}
