package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.runtime.simulation.AgentAbstractTickRuntime;
import server.agents.runtime.simulation.AgentSimulationMode;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentTickRuntimeTest {
    @Test
    void abstractModeBypassesFullTickPipeline() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        entry.simulationState().transitionTo(AgentSimulationMode.BACKGROUND_ABSTRACT, 1L);

        try (MockedStatic<AgentAbstractTickRuntime> abstractTicks =
                     mockStatic(AgentAbstractTickRuntime.class);
             MockedStatic<AgentTickCoreRuntime> liveTicks =
                     mockStatic(AgentTickCoreRuntime.class)) {
            AgentTickRuntime.tick(entry, 1, 2, ignored -> { }, ignored -> { });

            abstractTicks.verify(() -> AgentAbstractTickRuntime.tick(
                    org.mockito.ArgumentMatchers.eq(entry), anyLong()));
            liveTicks.verifyNoInteractions();
        }
    }
}
