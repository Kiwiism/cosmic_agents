package server.agents.capabilities.social.airshow;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentSessionLifecycleSideEffects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentAirshowServiceTest {
    @Test
    void startUsesAgentSessionLookupForNamedAgent() {
        Character owner = mock(Character.class);
        when(owner.getId()).thenReturn(123);

        try (MockedStatic<AgentSessionLifecycleSideEffects> lifecycle =
                     mockStatic(AgentSessionLifecycleSideEffects.class)) {
            lifecycle.when(() -> AgentSessionLifecycleSideEffects.getAgentEntry(123, "alpha"))
                    .thenReturn(null);

            String result = AgentAirshowService.start(owner, "alpha");

            assertEquals("No active owned bot named 'alpha'.", result);
            lifecycle.verify(() -> AgentSessionLifecycleSideEffects.getAgentEntry(123, "alpha"));
        }
    }
}
