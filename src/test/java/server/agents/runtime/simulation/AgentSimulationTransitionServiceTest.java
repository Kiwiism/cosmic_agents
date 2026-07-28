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
        assertEquals(AgentSimulationTransitionEvidence.Outcome.MATERIALIZATION_FAILED,
                entry.simulationState().lastTransitionEvidence().outcome());
    }

    @Test
    void abstractModeReconcilesBeforeMaterializing() {
        AgentRuntimeEntry entry = entry();
        entry.simulationState().allowAbstractExecution(AgentAbstractExecutionScope.TOWN_LIFE);
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
        assertEquals(AgentSimulationTransitionEvidence.Outcome.OUTCOME_RECONCILIATION_FAILED,
                entry.simulationState().lastTransitionEvidence().outcome());
    }

    @Test
    void successfulPresentationTransitionUpdatesEntryOwnedState() {
        AgentRuntimeEntry entry = entry();
        entry.simulationState().allowAbstractExecution(AgentAbstractExecutionScope.TOWN_LIFE);
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
        assertEquals(AgentSimulationTransitionEvidence.Outcome.APPLIED,
                entry.simulationState().lastTransitionEvidence().outcome());
    }

    @Test
    void abstractModeReconcilesAndMaterializesBeforeBackgroundActive() {
        AgentRuntimeEntry entry = entry();
        entry.simulationState().allowAbstractExecution(AgentAbstractExecutionScope.TOWN_LIFE);
        AgentSimulationTransitionService transitions = new AgentSimulationTransitionService(
                runtime -> true,
                AgentBackgroundOutcomeReconciler.ledgerBacked());

        transitions.transition(entry, AgentSimulationMode.BACKGROUND_ABSTRACT, 10L);
        assertEquals(true, entry.simulationState().backgroundOutcomes().snapshot().active());

        assertEquals(
                AgentSimulationMode.BACKGROUND_ACTIVE,
                transitions.transition(entry, AgentSimulationMode.BACKGROUND_ACTIVE, 20L));
        assertEquals(false, entry.simulationState().backgroundOutcomes().snapshot().active());
        assertEquals(1L,
                entry.simulationState().backgroundOutcomes().snapshot().reconciliationCount());
    }

    @Test
    void unsupportedAbstractOutcomeFailsClosed() {
        AgentRuntimeEntry entry = entry();
        entry.simulationState().allowAbstractExecution(AgentAbstractExecutionScope.TOWN_LIFE);
        AgentSimulationTransitionService transitions = new AgentSimulationTransitionService(
                runtime -> true,
                AgentBackgroundOutcomeReconciler.ledgerBacked());
        transitions.transition(entry, AgentSimulationMode.BACKGROUND_ABSTRACT, 10L);
        entry.simulationState().backgroundOutcomes().recordUnsupportedOutcome("inventory mutation");

        assertEquals(
                AgentSimulationMode.BACKGROUND_ABSTRACT,
                transitions.transition(entry, AgentSimulationMode.BACKGROUND_ACTIVE, 20L));
        assertEquals(AgentSimulationTransitionEvidence.Outcome.OUTCOME_RECONCILIATION_FAILED,
                entry.simulationState().lastTransitionEvidence().outcome());
    }

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(mock(Character.class), null, null);
    }
}
