package server.agents.progression.questwork;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.progression.questcatalog.AgentQuestCatalogRepository;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentQuestWorkUnitServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void beginsIdempotentlyAndPreventsConcurrentForegroundQuestWork() {
        AgentQuestWorkUnitService service = service();

        AgentQuestWorkUnit first = service.begin("work-1", "agent-1", 101, 2018, 100L);
        AgentQuestWorkUnit same = service.begin(" work-1 ", "agent-1", 101, 2018, 200L);

        assertEquals(first, same);
        assertThrows(IllegalStateException.class,
                () -> service.begin("work-1", "different-agent", 101, 2018, 250L));
        assertThrows(IllegalStateException.class,
                () -> service.begin("work-2", "agent-1", 101, 2032, 300L));
    }

    @Test
    void suspendsOnlyAtSafeBoundaryAndRestoresAcrossServiceRestart() {
        AgentQuestWorkUnitService service = service();
        service.begin("work-3", "agent-3", 103, 2018, 100L);

        AgentQuestWorkUnit requested = service.requestSuspend(
                "work-3", "resupply requested", false, 200L);
        AgentQuestWorkUnit suspended = service.observeSafeBoundary("work-3", 300L);
        AgentQuestWorkUnitService restarted = service();

        assertEquals(AgentQuestWorkPhase.SUSPEND_REQUESTED, requested.phase());
        assertTrue(suspended.suspended());
        assertEquals(suspended, restarted.restoreAll().getFirst());
        assertEquals(AgentQuestWorkPhase.ACTIVE,
                restarted.resume("work-3", 400L).phase());
    }

    @Test
    void reconcilesInventoryAndQuestCountersInsteadOfTrustingSavedCursor() {
        AgentQuestWorkUnitService service = service();
        service.begin("work-4", "agent-4", 104, 2018, 100L);

        AgentQuestWorkReconciliation hunting = service.reconcile("work-4",
                new AgentQuestLiveState(104, 20, 106010100, 1,
                        Map.of(4000034, 50, 4000042, 10, 2020000, 0), Map.of()),
                200L);
        AgentQuestWorkReconciliation returning = service.reconcile("work-4",
                new AgentQuestLiveState(104, 20, 106010100, 1,
                        Map.of(4000034, 100, 4000042, 10, 2020000, 1), Map.of()),
                300L);
        AgentQuestWorkReconciliation complete = service.reconcile("work-4",
                new AgentQuestLiveState(104, 20, 106010100, 2, Map.of(), Map.of()),
                400L);

        assertEquals(AgentQuestWorkAction.TRAVEL_TO_HUNT_MAP, hunting.nextAction());
        assertEquals(AgentQuestWorkAction.TURN_IN_QUEST, returning.nextAction());
        assertEquals(AgentQuestWorkPhase.COMPLETED, complete.workUnit().phase());
        assertEquals(AgentQuestWorkAction.COMPLETE, complete.nextAction());
    }

    @Test
    void persistsBoundedRetryEvidenceWithoutChangingQuestOwnership() {
        AgentQuestWorkUnitService service = service();
        service.begin("work-6", "agent-6", 106, 2018, 100L);

        AgentQuestWorkUnit retried = service.recordRetry(
                "work-6", "navigation edge rejected", 200L);

        assertEquals(1, retried.retryCount());
        assertEquals("navigation edge rejected", retried.lastReasonCode());
        assertEquals(retried, service().restoreAll().getFirst());
    }

    @Test
    void reselectsStageFromAuthoritativeQuestStateAfterRestart() {
        AgentQuestWorkUnitService service = service();
        service.begin("work-5", "agent-5", 105, 28273, 100L);
        service.requestSuspend("work-5", "external interruption", true, 150L);

        AgentQuestWorkReconciliation restored = service().reconcile("work-5",
                new AgentQuestLiveState(105, 15, 101000000, 0, Map.of(), Map.of()),
                200L);

        assertTrue(restored.workUnit().suspended());
        assertEquals(AgentQuestWorkStage.ACCEPT_QUEST, restored.workUnit().stage());
        assertEquals(AgentQuestWorkAction.WAIT, restored.nextAction());
    }

    private AgentQuestWorkUnitService service() {
        return new AgentQuestWorkUnitService(
                AgentQuestCatalogRepository.defaultRepository(),
                new AgentFileQuestWorkUnitStore(temporaryDirectory),
                new AgentQuestWorkReconciler());
    }
}
