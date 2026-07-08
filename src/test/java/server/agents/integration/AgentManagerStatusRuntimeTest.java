package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import server.agents.integration.AgentChatStatusRuntime;
import server.agents.capabilities.social.airshow.AgentAirshowStateRuntime;
import server.agents.integration.AgentManagerStatusRuntime;
import server.agents.runtime.AgentSchedulerRuntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentManagerStatusRuntimeTest {
    @Test
    void scheduleSpawnStatusCheckUsesAgentSchedulerThenChecksStatus() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character bot = mock(Character.class);
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);

        try (MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class);
             MockedStatic<AgentChatStatusRuntime> status = mockStatic(AgentChatStatusRuntime.class)) {
            AgentManagerStatusRuntime.scheduleSpawnStatusCheck(entry, bot, 1234L);

            scheduler.verify(() -> AgentSchedulerRuntime.afterDelay(
                    org.mockito.ArgumentMatchers.eq(1234L),
                    callback.capture()));
            callback.getValue().run();
            status.verify(() -> AgentChatStatusRuntime.checkBotStatus(entry, bot));
        }
    }

    @Test
    void managerStatusCallbacksDelegateToChatStatusRuntime() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);

        try (MockedStatic<AgentChatStatusRuntime> status = mockStatic(AgentChatStatusRuntime.class)) {
            AgentManagerStatusRuntime.checkManagerStatus(entry, bot);
            AgentManagerStatusRuntime.announceOwnerReturnedFromOffline(entry);
            AgentManagerStatusRuntime.tickAfkCheck(entry, owner);

            status.verify(() -> AgentChatStatusRuntime.checkBotStatus(entry, bot));
            status.verify(() -> AgentChatStatusRuntime.announceOwnerReturnedFromOffline(entry));
            status.verify(() -> AgentChatStatusRuntime.tickAfkCheck(entry, owner));
        }
    }

    @Test
    void adaptsAirshowActiveState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentManagerStatusRuntime.airshowActive(entry));

        AgentAirshowStateRuntime.start(entry);

        assertTrue(AgentManagerStatusRuntime.airshowActive(entry));
    }
}
