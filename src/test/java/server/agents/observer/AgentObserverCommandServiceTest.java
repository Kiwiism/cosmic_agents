package server.agents.observer;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class AgentObserverCommandServiceTest {
    @Test
    void issuerIsWatchedCharacterAndNamedCharacterIsObserver() {
        Character issuer = mock(Character.class);
        AgentObserverRuntime.StartResult started =
                new AgentObserverRuntime.StartResult(true, "started");

        try (MockedStatic<AgentObserverRuntime> runtime = mockStatic(AgentObserverRuntime.class)) {
            runtime.when(() -> AgentObserverRuntime.start(
                            eq(issuer), eq("Kiwi"), anyLong()))
                    .thenReturn(started);

            AgentObserverCommandService.execute(issuer, new String[]{"start", "Kiwi"});

            runtime.verify(() -> AgentObserverRuntime.start(
                    eq(issuer), eq("Kiwi"), anyLong()));
            verify(issuer).dropMessage(5, "started");
        }
    }

    @Test
    void usageNamesTheServerControlledObserver() {
        Character issuer = mock(Character.class);

        AgentObserverCommandService.execute(issuer, new String[0]);

        verify(issuer).dropMessage(
                5, "Usage: !observer start <observer IGN> | status | stop");
    }
}
