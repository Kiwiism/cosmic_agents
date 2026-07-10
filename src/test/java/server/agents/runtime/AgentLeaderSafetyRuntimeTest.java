package server.agents.runtime;

import server.agents.capabilities.recovery.AgentLeaderSafetyService;
import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentLeaderSafetyRuntimeTest {
    @Test
    void defaultInactiveLeaderTickUsesAgentRuntimeConfigTimeout() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        Character agent = mock(Character.class);
        List<String> calls = new ArrayList<>();

        try (MockedStatic<AgentLeaderSafetyService> service = mockStatic(AgentLeaderSafetyService.class)) {
            service.when(() -> AgentLeaderSafetyService.handleInactiveLeaderTick(
                            eq(entry),
                            eq(null),
                            eq(1234L),
                            any(AgentLeaderSafetyService.InactiveLeaderTickHooks.class)))
                    .thenAnswer(invocation -> {
                        AgentLeaderSafetyService.InactiveLeaderTickHooks hooks = invocation.getArgument(3);
                        calls.add("timeout:" + hooks.inactiveTownReturnMs());
                        return true;
                    });

            boolean handled = AgentLeaderSafetyRuntime.handleInactiveLeaderTick(
                    entry,
                    agent,
                    null,
                    1234L,
                    77);

            assertTrue(handled);
            assertEquals(List.of("timeout:" + AgentRuntimeConfig.cfg.OWNER_INACTIVE_TOWN_RETURN_MS), calls);
        }
    }

    @Test
    void delegatesInactiveLeaderTickThroughAgentRuntimeHooks() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        Character agent = mock(Character.class);
        List<String> calls = new ArrayList<>();

        try (MockedStatic<AgentLeaderSafetyService> service = mockStatic(AgentLeaderSafetyService.class)) {
            service.when(() -> AgentLeaderSafetyService.handleInactiveLeaderTick(
                            eq(entry),
                            eq(null),
                            eq(1234L),
                            any(AgentLeaderSafetyService.InactiveLeaderTickHooks.class)))
                    .thenAnswer(invocation -> {
                        AgentLeaderSafetyService.InactiveLeaderTickHooks hooks = invocation.getArgument(3);
                        calls.add("timeout:" + hooks.inactiveTownReturnMs());
                        return true;
                    });

            boolean handled = AgentLeaderSafetyRuntime.handleInactiveLeaderTick(
                    entry,
                    agent,
                    null,
                    1234L,
                    77,
                    5000L);

            assertTrue(handled);
            assertEquals(List.of("timeout:5000"), calls);
        }
    }
}
