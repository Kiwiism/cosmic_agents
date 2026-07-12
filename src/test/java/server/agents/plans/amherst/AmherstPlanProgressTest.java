package server.agents.plans.amherst;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.capabilities.runtime.AgentCapabilityResult;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AmherstPlanProgressTest {
    @TempDir
    Path tempDir;

    @Test
    void fileStoreRoundTripsOnlyDurableProgress() throws Exception {
        FileAmherstPlanProgressStore store = new FileAmherstPlanProgressStore(tempDir);
        AmherstPlanProgressService service = new AmherstPlanProgressService();
        AmherstPlanProgressSnapshot snapshot = AmherstPlanProgressSnapshot.empty("small-plan", 7);
        snapshot = service.start(snapshot, "q1000", 10L, 3);
        snapshot = service.terminal(snapshot, "q1000", AgentCapabilityResult.success("verified"), 20L, 9);

        store.save(snapshot);
        AmherstPlanProgressSnapshot loaded = store.load("small-plan", 7);

        assertEquals(snapshot, loaded);
        assertEquals(AmherstObjectiveProgressStatus.SATISFIED,
                loaded.objectives().get("q1000").status());
        assertEquals(3, loaded.objectives().get("q1000").capabilityJournalStart());
        assertEquals(9, loaded.objectives().get("q1000").capabilityJournalEnd());
    }

    @Test
    void repeatedEquivalentTransitionsAreIdempotent() {
        AmherstPlanProgressService service = new AmherstPlanProgressService();
        AmherstPlanProgressSnapshot snapshot = AmherstPlanProgressSnapshot.empty("small-plan", 7);
        snapshot = service.start(snapshot, "q1000", 10L, 0);
        AmherstPlanProgressSnapshot runningAgain = service.start(snapshot, "q1000", 11L, 0);
        assertSame(snapshot, runningAgain);

        snapshot = service.terminal(snapshot, "q1000", AgentCapabilityResult.success("verified"), 20L, 2);
        AmherstPlanProgressSnapshot terminalAgain = service.terminal(
                snapshot, "q1000", AgentCapabilityResult.success("verified"), 21L, 2);
        assertSame(snapshot, terminalAgain);
    }

    @Test
    void liveReconciliationSatisfiesMissingProgressAndReopensStaleSuccess() {
        AmherstPlanProgressService service = new AmherstPlanProgressService();
        AmherstPlanProgressSnapshot snapshot = AmherstPlanProgressSnapshot.empty("small-plan", 7);

        snapshot = service.reconcile(snapshot, "q1000", true, "live completed", 10L);
        assertEquals(AmherstObjectiveProgressStatus.SATISFIED,
                snapshot.objectives().get("q1000").status());

        snapshot = service.reconcile(snapshot, "q1000", false, "reset disproves completion", 20L);
        assertEquals(AmherstObjectiveProgressStatus.PENDING,
                snapshot.objectives().get("q1000").status());
        assertEquals(List.of(AmherstPlanJournalEventType.RECONCILED_SATISFIED,
                        AmherstPlanJournalEventType.RECONCILED_REOPENED),
                snapshot.journal().stream().map(AmherstPlanJournalEvent::type).toList());
    }

    @Test
    void interruptedRunningObjectiveBecomesPendingForARealNewAttempt() {
        AmherstPlanProgressService service = new AmherstPlanProgressService();
        AmherstPlanProgressSnapshot snapshot = AmherstPlanProgressSnapshot.empty("small-plan", 7);
        snapshot = service.start(snapshot, "q1000", 10L, 0);

        snapshot = service.recoverInterrupted(snapshot, 20L);
        assertEquals(AmherstObjectiveProgressStatus.PENDING,
                snapshot.objectives().get("q1000").status());
        snapshot = service.start(snapshot, "q1000", 30L, 2);
        assertEquals(2, snapshot.objectives().get("q1000").attempts());
    }

    @Test
    void fileStoreDeleteReturnsPlanToEmptyState() throws Exception {
        FileAmherstPlanProgressStore store = new FileAmherstPlanProgressStore(tempDir);
        AmherstPlanProgressSnapshot snapshot = AmherstPlanProgressSnapshot.empty("small-plan", 7);
        snapshot = new AmherstPlanProgressService().start(snapshot, "q1000", 10L, 0);
        store.save(snapshot);

        store.delete("small-plan", 7);

        assertEquals(AmherstPlanProgressSnapshot.empty("small-plan", 7),
                store.load("small-plan", 7));
    }
}
