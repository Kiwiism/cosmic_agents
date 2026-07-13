package server.agents.capabilities.supplies;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentAmmoRuntimeTest {
    @Test
    void ammoBridgeMethodsDelegateToBroadAgentRuntimes() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class);
             MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class)) {
            scheduler.when(() -> AgentSchedulerRuntime.randomDelayMs(900, 1400)).thenReturn(999L);

            AgentAmmoRuntime.sayMapNow(null, "ammo");
            AgentAmmoRuntime.afterDelay(entry, 500L, action);
            AgentAmmoRuntime.afterRandomDelay(entry, 900, 1100, action);
            long delay = AgentAmmoRuntime.randomDelayMs(900, 1400);

            replies.verify(() -> AgentReplyRuntime.sayMapNow(null, "ammo"));
            scheduler.verify(() -> AgentSchedulerRuntime.afterDelay(entry, 500L, action));
            scheduler.verify(() -> AgentSchedulerRuntime.afterRandomDelay(entry, 900, 1100, action));
            scheduler.verify(() -> AgentSchedulerRuntime.randomDelayMs(900, 1400));
            assertEquals(999L, delay);
        }
    }
}
