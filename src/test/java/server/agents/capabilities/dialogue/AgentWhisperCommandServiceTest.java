package server.agents.capabilities.dialogue;

import client.BotClient;
import client.Character;
import client.Client;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.commands.AgentReplyChannel;
import server.agents.commands.AgentReplyChannelStateRuntime;
import server.agents.runtime.AgentRuntimeHandle;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.integration.cosmic.CosmicAgentWhisperCommandBridge;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentWhisperCommandServiceTest {
    @Test
    void ignoresNonAgentTargets() {
        List<String> calls = new ArrayList<>();
        Character leader = character(1, mock(Client.class));
        Character target = character(2, mock(Client.class));

        AgentWhisperCommandService.handleWhisperToAgent(
                leader,
                target,
                "follow me",
                hooks(new TestHandle(), calls));

        assertEquals(List.of(), calls);
    }

    @Test
    void serviceRoutesAgentWhisperThroughHandleHooks() {
        List<String> calls = new ArrayList<>();
        Character leader = character(1, mock(Client.class));
        Character target = character(2, new BotClient(0, 0));
        TestHandle handle = new TestHandle();

        AgentWhisperCommandService.handleWhisperToAgent(
                leader,
                target,
                "follow me",
                hooks(handle, calls));

        assertEquals(List.of("resolve:1:2", "mark", "chat:follow me"), calls);
    }

    @Test
    void runtimeRoutesWhisperToTargetedAgentAndMarksWhisperReplyChannel() {
        Character leader = character(1, mock(Client.class));
        Character target = character(2, new BotClient(0, 0));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(target, leader, null);
        AgentRuntimeRegistry.clear();
        AgentRuntimeRegistry.registerEntry(leader.getId(), entry);

        try (MockedStatic<AgentChatRuntime> chat = mockStatic(AgentChatRuntime.class)) {
            CosmicAgentWhisperCommandBridge.handleWhisperToAgent(leader, target, "follow me");

            assertEquals(AgentReplyChannel.WHISPER, AgentReplyChannelStateRuntime.replyChannel(entry));
            chat.verify(() -> AgentChatRuntime.handleChat(eq("follow me"), any()));
        } finally {
            AgentRuntimeRegistry.clear();
        }
    }

    private static AgentWhisperCommandService.Hooks<TestHandle> hooks(TestHandle resolved, List<String> calls) {
        return new AgentWhisperCommandService.Hooks<>(
                (leader, target) -> {
                    calls.add("resolve:" + leader.getId() + ":" + target.getId());
                    return resolved;
                },
                entry -> calls.add("mark"),
                (entry, message) -> calls.add("chat:" + message));
    }

    private static Character character(int id, Client client) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getClient()).thenReturn(client);
        return character;
    }

    private static final class TestHandle implements AgentRuntimeHandle {
    }
}
