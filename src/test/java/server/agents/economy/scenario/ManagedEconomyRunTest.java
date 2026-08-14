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

    private static EconomyEvidencePipeline.Result cleanEvidence(EconomyEvidencePipeline evidence) {
        EconomyEvidencePipeline.Result result = mock(EconomyEvidencePipeline.Result.class);
        when(result.audit()).thenReturn(new server.agents.economy.persistence.JdbcEconomyInvariantAuditor.Audit(
                true, java.util.List.of()));
        when(evidence.process(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyInt())).thenReturn(result);
        return result;
    }
}
