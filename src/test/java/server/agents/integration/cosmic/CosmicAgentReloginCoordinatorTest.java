package server.agents.integration.cosmic;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.Logger;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentRegistrationCoordinator;
import server.agents.runtime.AgentRuntimeEntry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class CosmicAgentReloginCoordinatorTest {
    @Test
    void tickCallbackOverloadBuildsAgentRegistrationHook() {
        Character leader = mock(Character.class);
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        Logger log = mock(Logger.class);
        AgentLifecycleService.AgentTickCallback tickCallback = (activeEntry, leaderCharId, agentCharId) -> {
        };

        try (MockedStatic<AgentLifecycleService> lifecycle = mockStatic(AgentLifecycleService.class);
             MockedStatic<AgentRegistrationCoordinator> registration = mockStatic(AgentRegistrationCoordinator.class)) {
            registration.when(() -> AgentRegistrationCoordinator.registerAgent(100, leader, agent, true, tickCallback))
                    .thenReturn(entry);
            lifecycle.when(() -> AgentLifecycleService.reloginAgentQuietly(
                            eq(200),
                            eq(100),
                            eq(1),
                            eq(2),
                            any(AgentLifecycleService.ReloginHooks.class),
                            any(AgentLifecycleService.ReloginFailureLogger.class)))
                    .thenAnswer(invocation -> {
                        AgentLifecycleService.ReloginHooks hooks = invocation.getArgument(4);
                        hooks.registerSpawnedAgent().register(100, leader, agent);
                        return true;
                    });

            CosmicAgentReloginCoordinator.reloginAgent(200, 100, 1, 2, tickCallback, log);

            registration.verify(() -> AgentRegistrationCoordinator.registerAgent(100, leader, agent, true, tickCallback));
        }
    }
}
