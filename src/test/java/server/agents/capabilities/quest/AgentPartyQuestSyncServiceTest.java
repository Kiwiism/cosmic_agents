package server.agents.capabilities.quest;

import client.BotClient;
import client.Character;
import client.Client;
import client.QuestStatus;
import net.server.world.Party;
import org.junit.jupiter.api.Test;
import server.quest.Quest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentPartyQuestSyncServiceTest {
    @Test
    void findsOnlyOtherPartyMembersUsingBotClient() {
        Character source = character(1, mock(Client.class));
        Character agent = character(2, new BotClient(0, 0));
        Character player = character(3, mock(Client.class));
        when(source.getParty()).thenReturn(mock(Party.class));
        when(source.getPartyMembersOnline()).thenReturn(java.util.Arrays.asList(source, agent, player, null));

        assertEquals(List.of(agent), AgentPartyQuestSyncService.partyAgents(source));
    }

    @Test
    void ignoresSourceThatIsAlreadyAgentClient() {
        Character source = character(1, new BotClient(0, 0));
        when(source.getParty()).thenReturn(mock(Party.class));

        assertEquals(List.of(), AgentPartyQuestSyncService.partyAgents(source));
    }

    @Test
    void resolvesNpcFromFallbackThenSourceQuestThenMapleAdministrator() {
        Character source = character(1, mock(Client.class));
        Quest quest = mock(Quest.class);
        QuestStatus status = mock(QuestStatus.class);
        when(source.getQuest(quest)).thenReturn(status);

        assertEquals(2100, AgentPartyQuestSyncService.resolveQuestNpc(source, quest, 2100));

        when(status.getNpc()).thenReturn(2200);
        assertEquals(2200, AgentPartyQuestSyncService.resolveQuestNpc(source, quest, 0));

        when(status.getNpc()).thenReturn(0);
        assertEquals(constants.id.NpcId.MAPLE_ADMINISTRATOR,
                AgentPartyQuestSyncService.resolveQuestNpc(source, quest, 0));
    }

    @Test
    void startsQuestForPartyAgentsThatAreNotStarted() {
        Character source = character(1, mock(Client.class));
        Character agent = character(2, new BotClient(0, 0));
        Quest quest = mock(Quest.class);
        QuestStatus sourceStatus = mock(QuestStatus.class);
        QuestStatus agentStatus = mock(QuestStatus.class);
        when(source.getParty()).thenReturn(mock(Party.class));
        when(source.getPartyMembersOnline()).thenReturn(List.of(agent));
        when(source.getQuest(quest)).thenReturn(sourceStatus);
        when(sourceStatus.getNpc()).thenReturn(3000);
        when(agent.getQuest(quest)).thenReturn(agentStatus);
        when(agentStatus.getStatus()).thenReturn(QuestStatus.Status.NOT_STARTED);

        AgentPartyQuestSyncService.syncPartyAgentsQuestStart(source, quest, 0);

        verify(quest).forceStartWithActions(agent, 3000);
    }

    @Test
    void doesNotRestartAlreadyStartedPartyAgents() {
        Character source = character(1, mock(Client.class));
        Character agent = character(2, new BotClient(0, 0));
        Quest quest = mock(Quest.class);
        QuestStatus agentStatus = mock(QuestStatus.class);
        when(source.getParty()).thenReturn(mock(Party.class));
        when(source.getPartyMembersOnline()).thenReturn(List.of(agent));
        when(agent.getQuest(quest)).thenReturn(agentStatus);
        when(agentStatus.getStatus()).thenReturn(QuestStatus.Status.STARTED);

        AgentPartyQuestSyncService.syncPartyAgentsQuestStart(source, quest, 2000);

        verify(quest, never()).forceStartWithActions(agent, 2000);
    }

    private static Character character(int id, Client client) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getClient()).thenReturn(client);
        return character;
    }
}
