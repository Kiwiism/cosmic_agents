package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatStatusOrchestrator;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentLifecycleStatusCoordinatorTest {
    @Test
    void scheduleSpawnStatusCheckUsesSchedulerThenDialogueCapability() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);

        try (MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class);
             MockedStatic<AgentChatStatusOrchestrator> status = mockStatic(AgentChatStatusOrchestrator.class)) {
            AgentLifecycleStatusCoordinator.scheduleSpawnStatusCheck(entry, agent, 1234L);

            scheduler.verify(() -> AgentSchedulerRuntime.afterDelay(
                    org.mockito.ArgumentMatchers.eq(entry),
                    org.mockito.ArgumentMatchers.eq(1234L),
                    callback.capture()));
            callback.getValue().run();
            status.verify(() -> AgentChatStatusOrchestrator.checkBotStatus(entry, agent));
        }
    }
}
