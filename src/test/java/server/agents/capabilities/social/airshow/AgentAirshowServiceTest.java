package server.agents.capabilities.social.airshow;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotSessionLifecycleSideEffects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentAirshowServiceTest {
    @Test
    void startUsesAgentSessionLookupForNamedAgent() {
        Character owner = mock(Character.class);
        when(owner.getId()).thenReturn(123);

        try (MockedStatic<AgentBotSessionLifecycleSideEffects> lifecycle =
                     mockStatic(AgentBotSessionLifecycleSideEffects.class)) {
            lifecycle.when(() -> AgentBotSessionLifecycleSideEffects.getBotEntry(123, "alpha"))
                    .thenReturn(null);

            String result = AgentAirshowService.start(owner, "alpha");

            assertEquals("No active owned bot named 'alpha'.", result);
            lifecycle.verify(() -> AgentBotSessionLifecycleSideEffects.getBotEntry(123, "alpha"));
        }
    }
}
