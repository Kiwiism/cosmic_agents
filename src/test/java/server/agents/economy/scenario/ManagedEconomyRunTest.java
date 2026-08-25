package server.agents.economy.scenario;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import server.agents.economy.persistence.EconomyEvidencePipeline;
import server.agents.economy.persistence.SimulationRunRepository;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

class ManagedEconomyRunTest {
    @Test
    void logicalCheckpointEventsPersistAFullDomainCheckpointDuringLongAdvances() {
        EconomyRunApplication application = mock(EconomyRunApplication.class);
        EconomyEvidencePipeline evidence = mock(EconomyEvidencePipeline.class);
        SimulationRunRepository runs = mock(SimulationRunRepository.class);
        SimulationRunEngine.RunCheckpoint checkpoint = mock(SimulationRunEngine.RunCheckpoint.class);
        when(application.checkpoint()).thenReturn(checkpoint);

        new ManagedEconomyRun(application, evidence, runs, 100, true);
        ArgumentCaptor<Runnable> hook = ArgumentCaptor.forClass(Runnable.class);
        verify(application).onCheckpoint(hook.capture());

        hook.getValue().run();

        verify(runs).saveCheckpoint(checkpoint);
    }

    @Test
    void clampsAdvanceToConfiguredHorizonAndCompletesAfterCleanAudit() {
        EconomyRunApplication application = mock(EconomyRunApplication.class);
        EconomyEvidencePipeline evidence = mock(EconomyEvidencePipeline.class);
        SimulationRunRepository runs = mock(SimulationRunRepository.class);
        java.time.Instant now = java.time.Instant.parse("2026-01-01T00:00:00Z");
        java.time.Instant target = java.time.Instant.parse("2026-01-31T00:00:00Z");
        when(application.now()).thenReturn(now, target);
        when(application.targetAt()).thenReturn(target);
        var summary = new SimulationRunEngine.AdvanceSummary(target, 10, 1, 1, false, null);
        when(application.advanceTo(target)).thenReturn(summary);
        when(application.runId()).thenReturn(java.util.UUID.randomUUID());
        when(application.checkpoint()).thenReturn(mock(SimulationRunEngine.RunCheckpoint.class));
        EconomyEvidencePipeline.Result processed = cleanEvidence(evidence);
        ManagedEconomyRun managed = new ManagedEconomyRun(application, evidence, runs, 100, true);

        ManagedEconomyRun.AdvanceResult result = managed.advanceDays(90);

        assertEquals("COMPLETED", result.status());
        assertEquals("COMPLETED", managed.status());
        verify(application).advanceTo(target);
        verify(runs).updateLogicalTime(eq(application.runId()), eq(target), eq("COMPLETED"));
        assertThrows(IllegalStateException.class, () -> managed.advanceDays(1));
        assertEquals(processed, result.evidence());
    }

    @Test
    void refusesExplicitCompletionBeforeLogicalHorizon() {
        EconomyRunApplication application = mock(EconomyRunApplication.class);
        when(application.now()).thenReturn(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        when(application.targetAt()).thenReturn(java.time.Instant.parse("2026-01-31T00:00:00Z"));
        ManagedEconomyRun managed = new ManagedEconomyRun(application, mock(EconomyEvidencePipeline.class),
                mock(SimulationRunRepository.class), 100, true);

        assertThrows(IllegalStateException.class, managed::complete);
    }

    @Test
    void failurePersistsReasonAndMakesRunTerminal() {
        EconomyRunApplication application = mock(EconomyRunApplication.class);
        EconomyEvidencePipeline evidence = mock(EconomyEvidencePipeline.class);
        SimulationRunRepository runs = mock(SimulationRunRepository.class);
        java.util.UUID runId = java.util.UUID.randomUUID();
        java.time.Instant now = java.time.Instant.parse("2026-01-02T00:00:00Z");
        when(application.runId()).thenReturn(runId);
        when(application.now()).thenReturn(now);
        when(application.checkpoint()).thenReturn(mock(SimulationRunEngine.RunCheckpoint.class));
        cleanEvidence(evidence);
        ManagedEconomyRun managed = new ManagedEconomyRun(application, evidence, runs, 100, true);

        managed.fail("operator abort");

        verify(runs).updateStatus(runId, now, "FAILED", "operator abort");
        assertEquals("FAILED", managed.status());
        assertThrows(IllegalStateException.class, managed::audit);
    }

    @Test
    void cleanCalendarAdvancePersistsDayCloseManifest() {
        EconomyRunApplication application = mock(EconomyRunApplication.class);
        EconomyEvidencePipeline evidence = mock(EconomyEvidencePipeline.class);
        SimulationRunRepository runs = mock(SimulationRunRepository.class);
        java.util.UUID runId = java.util.UUID.randomUUID();
        java.time.Instant start = java.time.Instant.parse("2026-01-01T00:00:00Z");
        java.time.Instant boundary = java.time.Instant.parse("2026-01-02T00:00:00Z");
        java.time.Instant horizon = java.time.Instant.parse("2026-01-31T00:00:00Z");
        when(application.runId()).thenReturn(runId);
        when(application.now()).thenReturn(boundary);
        when(application.logicalStart()).thenReturn(start);
        when(application.nextDayBoundary()).thenReturn(boundary);
        when(application.targetAt()).thenReturn(horizon);
        when(application.advanceToDayBoundary(boundary)).thenReturn(
                new SimulationRunEngine.AdvanceSummary(boundary, 12, 2, 3, false, null));
        when(application.checkpoint()).thenReturn(new SimulationRunEngine.RunCheckpoint(runId, boundary,
                "config", "catalog", java.util.List.of(), java.util.Map.of(), java.util.Map.of()));
        cleanEvidence(evidence);
        ManagedEconomyRun managed = new ManagedEconomyRun(application, evidence, runs, 100, true);

        ManagedEconomyRun.AdvanceResult result = managed.advanceDay();

        assertEquals("DAY_CLOSED", result.status());
        assertEquals("DAY_CLOSED", managed.status());
        verify(runs).saveDayClose(org.mockito.ArgumentMatchers.argThat(close ->
                close.dayIndex() == 1 && close.dayClosedAt().equals(boundary) && close.auditClean()));
        verify(runs).updateLogicalTime(runId, boundary, "DAY_CLOSED");
    }

    @Test
    void holdingsMismatchBlocksBoundaryAndCanBeRetriedWithoutAdvancingTime() {
        EconomyRunApplication application = mock(EconomyRunApplication.class);
        EconomyEvidencePipeline evidence = mock(EconomyEvidencePipeline.class);
        SimulationRunRepository runs = mock(SimulationRunRepository.class);
        EconomyDayCloseReconciler reconciler = mock(EconomyDayCloseReconciler.class);
        java.util.UUID runId = java.util.UUID.randomUUID();
        java.time.Instant start = java.time.Instant.parse("2026-01-01T00:00:00Z");
        java.time.Instant boundary = start.plus(java.time.Duration.ofDays(1));
        when(application.runId()).thenReturn(runId);
        when(application.now()).thenReturn(boundary);
        when(application.logicalStart()).thenReturn(start);
        when(application.nextDayBoundary()).thenReturn(boundary.plus(java.time.Duration.ofDays(1)));
        when(application.targetAt()).thenReturn(start.plus(java.time.Duration.ofDays(30)));
        when(application.advanceToDayBoundary(boundary)).thenReturn(
                new SimulationRunEngine.AdvanceSummary(boundary, 12, 2, 3, false, null));
        when(application.agents()).thenReturn(java.util.Map.of());
        when(application.checkpoint()).thenReturn(new SimulationRunEngine.RunCheckpoint(runId, boundary,
                "config", "catalog", java.util.List.of(), java.util.Map.of(), java.util.Map.of()));
        cleanEvidence(evidence);
        when(reconciler.reconcile(runId, java.util.Map.of(), boundary))
                .thenReturn(new EconomyDayCloseReconciler.Result(false, java.util.List.of("MESO_MISMATCH")))
                .thenReturn(new EconomyDayCloseReconciler.Result(true, java.util.List.of()));
        ManagedEconomyRun managed = new ManagedEconomyRun(application, evidence, runs, 100, true,
                "RUNNING", reconciler);

        ManagedEconomyRun.AdvanceResult blocked = managed.advanceToDayBoundary(boundary);
        ManagedEconomyRun.AdvanceResult retried = managed.retryDayClose();

        assertEquals("DAY_CLOSE_BLOCKED", blocked.status());
        assertEquals("DAY_CLOSED", retried.status());
        verify(application, org.mockito.Mockito.times(1)).advanceToDayBoundary(boundary);
        verify(runs).updateLogicalTime(runId, boundary, "DAY_CLOSE_BLOCKED");
        verify(runs).saveDayClose(org.mockito.ArgumentMatchers.argThat(close -> close.dayIndex() == 1));
    }

    @Test
    void finalBoundaryIsReconciledAndManifestedBeforeCompletion() {
        EconomyRunApplication application = mock(EconomyRunApplication.class);
        EconomyEvidencePipeline evidence = mock(EconomyEvidencePipeline.class);
        SimulationRunRepository runs = mock(SimulationRunRepository.class);
        java.util.UUID runId = java.util.UUID.randomUUID();
        java.time.Instant start = java.time.Instant.parse("2026-01-01T00:00:00Z");
        java.time.Instant boundary = start.plus(java.time.Duration.ofDays(1));
        when(application.runId()).thenReturn(runId);
        when(application.now()).thenReturn(boundary);
        when(application.logicalStart()).thenReturn(start);
        when(application.nextDayBoundary()).thenReturn(boundary);
        when(application.targetAt()).thenReturn(boundary);
        when(application.advanceToDayBoundary(boundary)).thenReturn(
                new SimulationRunEngine.AdvanceSummary(boundary, 1, 1, 0, false, null));
        when(application.checkpoint()).thenReturn(new SimulationRunEngine.RunCheckpoint(runId, boundary,
                "config", "catalog", java.util.List.of(), java.util.Map.of(), java.util.Map.of()));
        cleanEvidence(evidence);
        ManagedEconomyRun managed = new ManagedEconomyRun(application, evidence, runs, 100, true);

        ManagedEconomyRun.AdvanceResult result = managed.advanceDay();

        assertEquals("COMPLETED", result.status());
        verify(runs).saveDayClose(org.mockito.ArgumentMatchers.any());
        verify(runs).updateLogicalTime(runId, boundary, "COMPLETED");
    }

    private static EconomyEvidencePipeline.Result cleanEvidence(EconomyEvidencePipeline evidence) {
        EconomyEvidencePipeline.Result result = mock(EconomyEvidencePipeline.Result.class);
        when(result.relay()).thenReturn(new server.agents.economy.persistence.EconomyOutboxRelay.Result(0, 0));
        when(result.ingestion()).thenReturn(
                new server.agents.economy.persistence.JdbcCosmicEconomicEventIngestor.Result(0, 0, null));
        when(result.audit()).thenReturn(new server.agents.economy.persistence.JdbcEconomyInvariantAuditor.Audit(
                true, java.util.List.of()));
        when(evidence.process(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyInt())).thenReturn(result);
        return result;
    }
}
