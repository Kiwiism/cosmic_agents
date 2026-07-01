package server.agents.capabilities.dialogue;

import client.BotClient;
import client.Character;
import client.Client;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.commands.AgentReplyChannel;
import server.agents.integration.AgentBotReplyChannelStateRuntime;
import server.agents.runtime.AgentRuntimeRegistry;
import server.bots.BotEntry;

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
        Character leader = character(1, mock(Client.class));
        Character target = character(2, mock(Client.class));
        AgentRuntimeRegistry.entriesByLeaderId().clear();

        try (MockedStatic<AgentChatRuntime> chat = mockStatic(AgentChatRuntime.class)) {
            AgentWhisperCommandService.handleWhisperToAgent(leader, target, "follow me");

            chat.verifyNoInteractions();
        }
    }

    @Test
    void routesWhisperToTargetedAgentAndMarksWhisperReplyChannel() {
        Character leader = character(1, mock(Client.class));
        Character target = character(2, new BotClient(0, 0));
        BotEntry entry = new BotEntry(target, leader, null);
        AgentRuntimeRegistry.entriesByLeaderId().clear();
        AgentRuntimeRegistry.entriesByLeaderId().put(leader.getId(), List.of(entry));

        try (MockedStatic<AgentChatRuntime> chat = mockStatic(AgentChatRuntime.class)) {
            AgentWhisperCommandService.handleWhisperToAgent(leader, target, "follow me");

            assertEquals(AgentReplyChannel.WHISPER, AgentBotReplyChannelStateRuntime.replyChannel(entry));
            chat.verify(() -> AgentChatRuntime.handleChat(eq("follow me"), any()));
        } finally {
            AgentRuntimeRegistry.entriesByLeaderId().clear();
        }
    }

    private static Character character(int id, Client client) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getClient()).thenReturn(client);
        return character;
    }
}
