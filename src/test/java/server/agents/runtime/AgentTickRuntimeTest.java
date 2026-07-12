package server.agents.runtime;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentTickRuntimeTest {
    @Test
    void tickUsesGuardedAgentTickOrchestrator() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        when(entry.transitionBarrierState()).thenReturn(new AgentTransitionBarrierState());

        try (MockedStatic<AgentTickOrchestrator> orchestrator = mockStatic(AgentTickOrchestrator.class)) {
            orchestrator.when(() -> AgentTickOrchestrator.runGuardedTick(
                            eq(entry),
                            eq(7),
                            eq(9),
                            any(AgentTickOrchestrator.TickCore.class),
                            any(AgentTickOrchestrator.TickFailureHandler.class)))
                    .thenAnswer(invocation -> null);

            AgentTickRuntime.tick(entry, 7, 9, tickEntry -> { }, tickEntry -> { });

            orchestrator.verify(() -> AgentTickOrchestrator.runGuardedTick(
                    eq(entry),
                    eq(7),
                    eq(9),
                    any(AgentTickOrchestrator.TickCore.class),
                    any(AgentTickOrchestrator.TickFailureHandler.class)));
        }
    }
}
