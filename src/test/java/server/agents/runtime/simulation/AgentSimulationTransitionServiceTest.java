package server.agents.runtime.simulation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentSimulationTransitionServiceTest {
    @Test
    void backgroundActiveMustMaterializeBeforePresentation() {
        AgentRuntimeEntry entry = entry();
        entry.simulationState().transitionTo(AgentSimulationMode.BACKGROUND_ACTIVE, 10L);
        AgentSimulationTransitionService transitions = new AgentSimulationTransitionService(
                runtime -> false,
                AgentBackgroundOutcomeReconciler.noPendingOutcomes());

        assertEquals(
                AgentSimulationMode.BACKGROUND_ACTIVE,
                transitions.transition(entry, AgentSimulationMode.PRESENTATION, 20L));
        assertEquals(AgentSimulationMode.BACKGROUND_ACTIVE, entry.simulationState().mode());
    }

    @Test
    void abstractModeReconcilesBeforeMaterializing() {
        AgentRuntimeEntry entry = entry();
        entry.simulationState().transitionTo(AgentSimulationMode.BACKGROUND_ABSTRACT, 10L);
        AtomicInteger materializations = new AtomicInteger();
        AgentSimulationTransitionService transitions = new AgentSimulationTransitionService(
                runtime -> {
                    materializations.incrementAndGet();
                    return true;
                },
                runtime -> false);

        assertEquals(
                AgentSimulationMode.BACKGROUND_ABSTRACT,
                transitions.transition(entry, AgentSimulationMode.PRESENTATION, 20L));
        assertEquals(0, materializations.get());
    }

    @Test
    void successfulPresentationTransitionUpdatesEntryOwnedState() {
        AgentRuntimeEntry entry = entry();
        AgentSimulationTransitionService transitions = new AgentSimulationTransitionService(
                runtime -> true,
                runtime -> true);

        transitions.transition(entry, AgentSimulationMode.BACKGROUND_ABSTRACT, 10L);
        assertEquals(
                AgentSimulationMode.PRESENTATION,
                transitions.transition(entry, AgentSimulationMode.PRESENTATION, 20L));
        assertEquals(AgentSimulationMode.PRESENTATION, entry.simulationState().mode());
        assertEquals(20L, entry.simulationState().modeSinceMs());
        assertEquals(2L, entry.simulationState().transitionCount());
    }

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(mock(Character.class), null, null);
    }
}
