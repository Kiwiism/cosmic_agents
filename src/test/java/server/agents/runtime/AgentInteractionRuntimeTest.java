package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.Logger;
import server.agents.commands.AgentReplyChannel;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentInteractionRuntimeTest {
    @Test
    void spawnDelegatesToAgentSpawnRuntimeWithAgentCallbacks() {
        Character leader = mock(Character.class);
        Character agent = mock(Character.class);
        AgentLifecycleService.AgentSpawnResult expected = AgentLifecycleService.AgentSpawnResult.ok(agent, false);

        try (MockedStatic<AgentSpawnRuntime> spawnRuntime = mockStatic(AgentSpawnRuntime.class)) {
            spawnRuntime.when(() -> AgentSpawnRuntime.spawnAgentForLeader(
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
    void chatDelegatesToAgentChatRouteRuntimeWithLifecycleCallbacks() {
        Character leader = mock(Character.class);

        try (MockedStatic<AgentChatRouteRuntime> chatRuntime = mockStatic(AgentChatRouteRuntime.class)) {
            AgentInteractionRuntime.handleLeaderChat(leader, "follow me", AgentReplyChannel.MAP);

            chatRuntime.verify(() -> AgentChatRouteRuntime.handleChat(
                    eq(leader),
                    eq("follow me"),
                    eq(AgentReplyChannel.MAP),
                    any(),
                    any(),
                    any()));
        }
    }

    @Test
    void reloginDelegatesToAgentReloginRuntimeWithAgentTickCallback() {
        try (MockedStatic<AgentReloginRuntime> reloginRuntime = mockStatic(AgentReloginRuntime.class)) {
            AgentInteractionRuntime.reloginAgent(11, 22, 0, 1);

            reloginRuntime.verify(() -> AgentReloginRuntime.reloginAgent(
                    eq(11),
                    eq(22),
                    eq(0),
                    eq(1),
                    any(AgentLifecycleService.AgentTickCallback.class),
                    any(Logger.class)));
        }
    }
}
