package server.agents.capabilities.supplies;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentSchedulerRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentAmmoRuntimeTest {
    @Test
    void ammoBridgeMethodsDelegateToBroadAgentRuntimes() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class);
             MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class)) {
            scheduler.when(() -> AgentSchedulerRuntime.randomDelayMs(900, 1400)).thenReturn(999L);

            AgentAmmoRuntime.sayMapNow(null, "ammo");
            AgentAmmoRuntime.afterDelay(500L, action);
            AgentAmmoRuntime.afterRandomDelay(900, 1100, action);
            long delay = AgentAmmoRuntime.randomDelayMs(900, 1400);

            replies.verify(() -> AgentReplyRuntime.sayMapNow(null, "ammo"));
            scheduler.verify(() -> AgentSchedulerRuntime.afterDelay(500L, action));
            scheduler.verify(() -> AgentSchedulerRuntime.afterRandomDelay(900, 1100, action));
            scheduler.verify(() -> AgentSchedulerRuntime.randomDelayMs(900, 1400));
            assertEquals(999L, delay);
        }
    }
}
