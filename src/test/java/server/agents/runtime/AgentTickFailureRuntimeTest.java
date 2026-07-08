package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentMovementCommandRuntime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentTickFailureRuntimeTest {
    @Test
    void defaultOverloadDelegatesToFailurePolicyWithRuntimeHooks() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        RuntimeException failure = new RuntimeException("boom");

        try (MockedStatic<AgentTickFailurePolicy> policy = mockStatic(AgentTickFailurePolicy.class);
             MockedStatic<AgentMovementCommandRuntime> movement = mockStatic(AgentMovementCommandRuntime.class)) {
            policy.when(() -> AgentTickFailurePolicy.handleFailure(
                            eq(entry),
                            eq(100),
                            eq(200),
                            eq(failure),
                            any(Long.class),
                            any(AgentTickFailurePolicy.FailureHooks.class)))
                    .thenAnswer(invocation -> null);

            AgentTickFailureRuntime.handleFailure(entry, 100, 200, failure);

            policy.verify(() -> AgentTickFailurePolicy.handleFailure(
                    eq(entry),
                    eq(100),
                    eq(200),
                    eq(failure),
                    any(Long.class),
                    any(AgentTickFailurePolicy.FailureHooks.class)));
        }
    }
}
