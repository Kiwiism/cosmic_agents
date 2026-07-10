package server.agents.runtime;

import server.agents.capabilities.dialogue.AgentChatRouteCoordinator;
import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.cosmic.CosmicAgentSpawnCoordinator;
import server.agents.integration.cosmic.CosmicAgentReloginCoordinator;
import org.slf4j.Logger;
import server.agents.commands.AgentReplyChannel;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentInteractionRuntimeTest {
    @Test
    void registerAgentDelegatesToAgentRegistrationCoordinatorWithAgentTickCallback() {
        Character leader = mock(Character.class);
        Character agent = mock(Character.class);

        try (MockedStatic<AgentRegistrationCoordinator> registrationRuntime = mockStatic(AgentRegistrationCoordinator.class)) {
            AgentInteractionRuntime.registerAgent(22, leader, agent);

            registrationRuntime.verify(() -> AgentRegistrationCoordinator.registerManualAgent(
                    eq(22),
                    eq(leader),
                    eq(agent),
                    any(AgentLifecycleService.AgentTickCallback.class)));
        }
    }

    @Test
    void registerSpawnedAgentDelegatesToAgentRegistrationCoordinatorWithAgentTickCallback() {
        Character leader = mock(Character.class);
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);

        try (MockedStatic<AgentRegistrationCoordinator> registrationRuntime = mockStatic(AgentRegistrationCoordinator.class)) {
            registrationRuntime.when(() -> AgentRegistrationCoordinator.registerSpawnedAgent(
                            eq(22),
                            eq(leader),
                            eq(agent),
                            any(AgentLifecycleService.AgentTickCallback.class)))
                    .thenReturn(entry);

            org.junit.jupiter.api.Assertions.assertSame(entry,
                    AgentInteractionRuntime.registerSpawnedAgent(22, leader, agent));
        }
    }

    @Test
    void spawnDelegatesToCosmicAgentSpawnCoordinatorWithAgentCallbacks() {
        Character leader = mock(Character.class);
        Character agent = mock(Character.class);
        AgentLifecycleService.AgentSpawnResult expected = AgentLifecycleService.AgentSpawnResult.ok(agent, false);

        try (MockedStatic<CosmicAgentSpawnCoordinator> spawnRuntime = mockStatic(CosmicAgentSpawnCoordinator.class)) {
            spawnRuntime.when(() -> CosmicAgentSpawnCoordinator.spawnAgentForLeader(
                            eq(leader),
                            eq("AgentA"),
                            any(AgentLifecycleService.AgentTickCallback.class),
                            any(Consumer.class),
                            any(Logger.class)))
                    .thenReturn(expected);

            AgentLifecycleService.AgentSpawnResult result = AgentInteractionRuntime.spawnAgentForLeader(leader, "AgentA");

            org.junit.jupiter.api.Assertions.assertSame(expected, result);
        }
    }

    @Test
    void chatDelegatesToAgentChatRouteCoordinatorWithLifecycleCallbacks() {
        Character leader = mock(Character.class);

        try (MockedStatic<AgentChatRouteCoordinator> chatRuntime = mockStatic(AgentChatRouteCoordinator.class)) {
            AgentInteractionRuntime.handleLeaderChat(leader, "follow me", AgentReplyChannel.MAP);

            chatRuntime.verify(() -> AgentChatRouteCoordinator.handleChat(
                    eq(leader),
                    eq("follow me"),
                    eq(AgentReplyChannel.MAP),
                    any(),
                    any(),
                    any()));
        }
    }

    @Test
    void reloginDelegatesToCosmicAgentReloginCoordinatorWithAgentTickCallback() {
        try (MockedStatic<CosmicAgentReloginCoordinator> reloginRuntime = mockStatic(CosmicAgentReloginCoordinator.class)) {
            AgentInteractionRuntime.reloginAgent(11, 22, 0, 1);

            reloginRuntime.verify(() -> CosmicAgentReloginCoordinator.reloginAgent(
                    eq(11),
                    eq(22),
                    eq(0),
                    eq(1),
                    any(AgentLifecycleService.AgentTickCallback.class),
                    any(Logger.class)));
        }
    }
}
