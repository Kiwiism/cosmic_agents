package server.agents.economy.scenario;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import server.agents.economy.persistence.EconomyEvidencePipeline;
import server.agents.economy.persistence.SimulationRunRepository;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
