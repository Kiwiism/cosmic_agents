package server.agents.runtime;

import server.agents.capabilities.recovery.AgentLeaderSafetyService;
import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentLeaderSafetyCoordinatorTest {
    @Test
    void defaultInactiveLeaderTickUsesAgentRuntimeConfigTimeout() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        Character agent = mock(Character.class);
        when(entry.relationshipState()).thenReturn(new AgentRelationshipState(agent, 0L, 0L));
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

            boolean handled = AgentLeaderSafetyCoordinator.handleInactiveLeaderTick(
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
        when(entry.relationshipState()).thenReturn(new AgentRelationshipState(agent, 0L, 0L));
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

            boolean handled = AgentLeaderSafetyCoordinator.handleInactiveLeaderTick(
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

    @Test
    void ignoresInactiveLeaderRecoveryWithoutExplicitRelationship() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        Character agent = mock(Character.class);
        when(entry.relationshipState()).thenReturn(new AgentRelationshipState(null, 0L, 0L));

        assertFalse(AgentLeaderSafetyCoordinator.handleInactiveLeaderTick(
                entry, agent, null, 1234L, 77));
    }
}
