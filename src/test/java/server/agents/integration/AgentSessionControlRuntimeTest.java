package server.agents.integration;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentSessionControlRuntimeTest {
    @Test
    void primarySessionUsesFirstEntryForLeader() {
        Character owner = mock(Character.class);
        when(owner.getId()).thenReturn(123);
        AgentRuntimeEntry first = new AgentRuntimeEntry(null, owner, null);
        AgentRuntimeEntry second = new AgentRuntimeEntry(null, owner, null);

        try (MockedStatic<AgentSessionLifecycleSideEffects> lifecycle =
                     mockStatic(AgentSessionLifecycleSideEffects.class)) {
            lifecycle.when(() -> AgentSessionLifecycleSideEffects.getBotEntries(123))
                    .thenReturn(List.of(first, second));

            assertTrue(AgentSessionControlRuntime.isPrimarySession(first));
            assertFalse(AgentSessionControlRuntime.isPrimarySession(second));
        }
    }

    @Test
    void ownerAwaySafeModeUsesLifecycleSideEffectBoundary() {
        try (MockedStatic<AgentSessionLifecycleSideEffects> lifecycle =
                     mockStatic(AgentSessionLifecycleSideEffects.class)) {
            AgentSessionControlRuntime.issueOwnerAwaySafeModeForLeader(123, true);

            lifecycle.verify(() -> AgentSessionLifecycleSideEffects.issueOwnerAwaySafeModeForLeader(123, true));
        }
    }
}
