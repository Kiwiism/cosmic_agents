package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotAmmoReplyRuntime;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotAmmoRuntime;
import server.agents.integration.AgentBotAmmoSchedulerRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotAmmoRuntimeTest {
    @Test
    void ammoBridgeMethodsDelegateToAmmoAdapters() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotAmmoReplyRuntime> replies = mockStatic(AgentBotAmmoReplyRuntime.class);
             MockedStatic<AgentBotAmmoSchedulerRuntime> scheduler =
                     mockStatic(AgentBotAmmoSchedulerRuntime.class)) {
            scheduler.when(() -> AgentBotAmmoSchedulerRuntime.randomDelayMs(900, 1400)).thenReturn(999L);

            AgentBotAmmoRuntime.sayMapNow(null, "ammo");
            AgentBotAmmoRuntime.afterDelay(500L, action);
            AgentBotAmmoRuntime.afterRandomDelay(900, 1100, action);
            long delay = AgentBotAmmoRuntime.randomDelayMs(900, 1400);

            replies.verify(() -> AgentBotAmmoReplyRuntime.sayMapNow(null, "ammo"));
            scheduler.verify(() -> AgentBotAmmoSchedulerRuntime.afterDelay(500L, action));
            scheduler.verify(() -> AgentBotAmmoSchedulerRuntime.afterRandomDelay(900, 1100, action));
            scheduler.verify(() -> AgentBotAmmoSchedulerRuntime.randomDelayMs(900, 1400));
            assertEquals(999L, delay);
        }
    }

    @Test
    void ammoReplyAdapterDelegatesToBroadReplyRuntime() {
        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotAmmoReplyRuntime.sayMapNow(null, "ammo");

            replies.verify(() -> AgentBotReplyRuntime.sayMapNow(null, "ammo"));
        }
    }

    @Test
    void ammoSchedulerAdapterDelegatesToBroadSchedulerRuntime() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            scheduler.when(() -> AgentBotSchedulerRuntime.randomDelayMs(900, 1400)).thenReturn(999L);

            AgentBotAmmoSchedulerRuntime.afterDelay(500L, action);
            AgentBotAmmoSchedulerRuntime.afterRandomDelay(900, 1100, action);
            long delay = AgentBotAmmoSchedulerRuntime.randomDelayMs(900, 1400);

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterDelay(500L, action));
            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(900, 1100, action));
            scheduler.verify(() -> AgentBotSchedulerRuntime.randomDelayMs(900, 1400));
            assertEquals(999L, delay);
        }
    }
}
