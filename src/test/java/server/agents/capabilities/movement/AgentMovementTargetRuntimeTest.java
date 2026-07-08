package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentMovementTargetSideEffects;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentMovementTargetRuntimeTest {
    @Test
    void snapshotDelegatesToIntegrationSideEffectCapture() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentMovementTargetSnapshot snapshot = mock(AgentMovementTargetSnapshot.class);

        try (MockedStatic<AgentMovementTargetSideEffects> sideEffects =
                     mockStatic(AgentMovementTargetSideEffects.class)) {
            sideEffects.when(() -> AgentMovementTargetSideEffects.captureTargetSnapshot(entry))
                    .thenReturn(snapshot);

            assertSame(snapshot, AgentMovementTargetRuntime.snapshot(entry));
            sideEffects.verify(() -> AgentMovementTargetSideEffects.captureTargetSnapshot(entry));
        }
    }
}
