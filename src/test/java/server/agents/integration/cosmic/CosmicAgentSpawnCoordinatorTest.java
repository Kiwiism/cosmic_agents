package server.agents.integration.cosmic;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.Logger;
import server.agents.auth.AgentOwnershipService;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentRegistrationCoordinator;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class CosmicAgentSpawnCoordinatorTest {
    @Test
    void tickCallbackOverloadBuildsAgentRegistrationHook() {
        Character leader = mock(Character.class);
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        Logger log = mock(Logger.class);
        AgentLifecycleService.AgentTickCallback tickCallback = (activeEntry, leaderCharId, agentCharId) -> {
        };
        when(leader.getId()).thenReturn(100);

        try (MockedStatic<AgentLifecycleService> lifecycle = mockStatic(AgentLifecycleService.class);
             MockedStatic<AgentRegistrationCoordinator> registration = mockStatic(AgentRegistrationCoordinator.class)) {
            registration.when(() -> AgentRegistrationCoordinator.registerAgent(100, leader, agent, true, tickCallback))
                    .thenReturn(entry);
            lifecycle.when(() -> AgentLifecycleService.spawnAgentForLeaderQuietly(
                            eq(leader),
                            eq("Alpha"),
                            any(AgentOwnershipService.class),
                            any(AgentLifecycleService.SpawnHooks.class),
                            any(AgentLifecycleService.SpawnFailureLogger.class)))
                    .thenAnswer(invocation -> {
                        AgentLifecycleService.SpawnHooks hooks = invocation.getArgument(3);
                        assertSame(entry, hooks.registerSpawnedAgent().register(100, leader, agent));
                        return AgentLifecycleService.AgentSpawnResult.ok(agent, false);
                    });

            AgentLifecycleService.AgentSpawnResult result = CosmicAgentSpawnCoordinator.spawnAgentForLeader(
                    leader,
                    "Alpha",
                    tickCallback,
                    ignored -> {
                    },
                    log);

            assertTrue(result.success());
            assertSame(agent, result.agent());
            registration.verify(() -> AgentRegistrationCoordinator.registerAgent(100, leader, agent, true, tickCallback));
        }
    }
}
