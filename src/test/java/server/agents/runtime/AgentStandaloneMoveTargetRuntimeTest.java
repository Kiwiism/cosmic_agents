package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.bots.BotEntry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentStandaloneMoveTargetRuntimeTest {
    @Test
    void configBoundOverloadDelegatesToStandaloneMoveTargetService() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
        Character agent = mock(Character.class);

        try (MockedStatic<AgentStandaloneMoveTargetTickService> service =
                     mockStatic(AgentStandaloneMoveTargetTickService.class)) {
            service.when(() -> AgentStandaloneMoveTargetTickService.tickStandaloneMoveTarget(
                            eq(entry),
                            eq(agent),
                            eq(false),
                            any(AgentStandaloneMoveTargetTickService.Hooks.class)))
                    .thenAnswer(invocation -> null);

            AgentStandaloneMoveTargetRuntime.tickStandaloneMoveTarget(entry, agent, false);

            service.verify(() -> AgentStandaloneMoveTargetTickService.tickStandaloneMoveTarget(
                    eq(entry),
                    eq(agent),
                    eq(false),
                    any(AgentStandaloneMoveTargetTickService.Hooks.class)));
        }
    }
}
