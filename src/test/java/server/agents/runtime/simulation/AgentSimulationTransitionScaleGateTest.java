package server.agents.runtime.simulation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSimulationTransitionScaleGateTest {
    private static final int AGENT_COUNT = 2_000;

    @Test
    void twoThousandAgentsRoundTripWithoutStateDivergence() {
        AtomicInteger materializations = new AtomicInteger();
        AgentSimulationTransitionService transitions = new AgentSimulationTransitionService(
                entry -> {
                    materializations.incrementAndGet();
                    return true;
                },
                AgentBackgroundOutcomeReconciler.ledgerBacked());
        List<AgentRuntimeEntry> entries = new ArrayList<>(AGENT_COUNT);

        for (int id = 1; id <= AGENT_COUNT; id++) {
            Character agent = mock(Character.class);
            when(agent.getId()).thenReturn(id);
            when(agent.getMapId()).thenReturn(100000000 + id % 20);
            when(agent.getPosition()).thenReturn(new Point(id % 800, id % 500));
            AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
            entry.simulationState().allowAbstractExecution(AgentAbstractExecutionScope.TOWN_LIFE);
            entries.add(entry);

            assertEquals(AgentSimulationMode.BACKGROUND_ACTIVE,
                    transitions.transition(entry, AgentSimulationMode.BACKGROUND_ACTIVE, 10L));
            assertEquals(AgentSimulationMode.BACKGROUND_ABSTRACT,
                    transitions.transition(entry, AgentSimulationMode.BACKGROUND_ABSTRACT, 20L));
            assertEquals(AgentSimulationMode.PRESENTATION,
                    transitions.transition(entry, AgentSimulationMode.PRESENTATION, 30L));
        }

        assertEquals(AGENT_COUNT, materializations.get());
        for (AgentRuntimeEntry entry : entries) {
            assertEquals(3L, entry.simulationState().transitionCount());
            assertEquals(1L, entry.simulationState().backgroundOutcomes().snapshot().reconciliationCount());
            assertFalse(entry.simulationState().backgroundOutcomes().snapshot().active());
            assertEquals(AgentSimulationTransitionEvidence.Outcome.APPLIED,
                    entry.simulationState().lastTransitionEvidence().outcome());
        }
    }
}
