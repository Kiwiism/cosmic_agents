package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotChatStatusRuntime;
import server.agents.integration.AgentBotAirshowStateRuntime;
import server.agents.integration.AgentBotManagerSchedulerRuntime;
import server.agents.integration.AgentBotManagerStatusRuntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotManagerStatusRuntimeTest {
    @Test
    void scheduleSpawnStatusCheckUsesAgentSchedulerThenChecksStatus() {
        BotEntry entry = new BotEntry(null, null, null);
        Character bot = mock(Character.class);
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);

        try (MockedStatic<AgentBotManagerSchedulerRuntime> scheduler = mockStatic(AgentBotManagerSchedulerRuntime.class);
             MockedStatic<AgentBotChatStatusRuntime> status = mockStatic(AgentBotChatStatusRuntime.class)) {
            AgentBotManagerStatusRuntime.scheduleSpawnStatusCheck(entry, bot, 1234L);

            scheduler.verify(() -> AgentBotManagerSchedulerRuntime.afterDelay(
                    org.mockito.ArgumentMatchers.eq(1234L),
                    callback.capture()));
            callback.getValue().run();
            status.verify(() -> AgentBotChatStatusRuntime.checkBotStatus(entry, bot));
        }
    }

    @Test
    void managerStatusCallbacksDelegateToChatStatusRuntime() {
        BotEntry entry = new BotEntry(null, null, null);
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);

        try (MockedStatic<AgentBotChatStatusRuntime> status = mockStatic(AgentBotChatStatusRuntime.class)) {
            AgentBotManagerStatusRuntime.checkManagerStatus(entry, bot);
            AgentBotManagerStatusRuntime.announceOwnerReturnedFromOffline(entry);
            AgentBotManagerStatusRuntime.tickAfkCheck(entry, owner);

            status.verify(() -> AgentBotChatStatusRuntime.checkBotStatus(entry, bot));
            status.verify(() -> AgentBotChatStatusRuntime.announceOwnerReturnedFromOffline(entry));
            status.verify(() -> AgentBotChatStatusRuntime.tickAfkCheck(entry, owner));
        }
    }

    @Test
    void adaptsAirshowActiveState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotManagerStatusRuntime.airshowActive(entry));

        AgentBotAirshowStateRuntime.start(entry);

        assertTrue(AgentBotManagerStatusRuntime.airshowActive(entry));
    }
}
