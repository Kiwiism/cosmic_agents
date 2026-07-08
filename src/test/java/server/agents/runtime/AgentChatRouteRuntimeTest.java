package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatIngressService;
import server.agents.commands.AgentReplyChannel;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentChatRouteRuntimeTest {
    @Test
    void compactChatRouteUsesAgentRuntimeDefaults() {
        Character leader = mock(Character.class);
        AgentReplyChannel channel = AgentReplyChannel.MAP;

        try (MockedStatic<AgentChatIngressService> ingress = mockStatic(AgentChatIngressService.class)) {
            AgentChatRouteRuntime.handleChat(
                    leader,
                    "follow me",
                    channel,
                    (leaderId, commandLeader, agentName) -> null,
                    (leaderId, commandLeader, agentName, targetName) -> null,
                    (leaderId, agentName) -> true);

            ingress.verify(() -> AgentChatIngressService.handleChat(
                    eq(leader),
                    eq("follow me"),
                    eq(channel),
                    any(AgentChatIngressService.Hooks.class)));
        }
    }

    @Test
    void delegatesChatThroughAgentIngressHooks() {
        Character leader = mock(Character.class);
        AgentReplyChannel channel = AgentReplyChannel.MAP;
        Map<Integer, List<AgentRuntimeEntry>> entries = new ConcurrentHashMap<>();

        try (MockedStatic<AgentChatIngressService> ingress = mockStatic(AgentChatIngressService.class)) {
            AgentChatRouteRuntime.handleChat(
                    leader,
                    "follow me",
                    channel,
                    entries,
                    (leaderId, commandLeader, agentName) -> null,
                    (leaderId, commandLeader, agentName, targetName) -> null,
                    (leaderId, agentName) -> true,
                    new AgentFormationService.FormationState(AgentFormationService.FormationType.STAGGER, 60, 0),
                    60,
                    120);

            ingress.verify(() -> AgentChatIngressService.handleChat(
                    eq(leader),
                    eq("follow me"),
                    eq(channel),
                    any(AgentChatIngressService.Hooks.class)));
        }
    }
}
