package server.agents.capabilities.social.airshow;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionLifecycleRuntime;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentAirshowServiceTest {
    @AfterEach
    void clearSchedulerMode() {
        System.clearProperty("agents.scheduler.mode");
    }

    @Test
    void startUsesAgentSessionLookupForNamedAgent() {
        Character owner = mock(Character.class);
        when(owner.getId()).thenReturn(123);

        try (MockedStatic<AgentSessionLifecycleRuntime> lifecycle =
                     mockStatic(AgentSessionLifecycleRuntime.class)) {
            lifecycle.when(() -> AgentSessionLifecycleRuntime.getAgentEntry(123, "alpha"))
                    .thenReturn(null);

            String result = AgentAirshowService.startAsync(owner, "alpha").join();

            assertEquals("No active owned bot named 'alpha'.", result);
            lifecycle.verify(() -> AgentSessionLifecycleRuntime.getAgentEntry(123, "alpha"));
        }
    }

    @Test
    void centralModeRoutesStartThroughAgentMailbox() {
        System.setProperty("agents.scheduler.mode", "central-sequential");
        Character owner = mock(Character.class);
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, owner, null);
        when(owner.getId()).thenReturn(123);

        try (MockedStatic<AgentSessionLifecycleRuntime> lifecycle =
                     mockStatic(AgentSessionLifecycleRuntime.class)) {
            lifecycle.when(() -> AgentSessionLifecycleRuntime.getAgentEntry(123, "alpha"))
                    .thenReturn(entry);

            CompletableFuture<String> result = AgentAirshowService.startAsync(owner, "alpha");

            assertFalse(result.isDone());
            assertEquals(1, entry.actionMailbox().size());
            entry.actionMailbox().drain(entry, 1);
            assertEquals("Bot 'alpha' must be in your map.", result.join());
        }
    }
}
