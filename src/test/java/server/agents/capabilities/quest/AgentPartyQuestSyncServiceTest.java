package server.agents.capabilities.quest;

import client.BotClient;
import client.Character;
import client.Client;
import client.QuestStatus;
import net.server.world.Party;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentQuestSyncGateway;
import server.agents.integration.AgentQuestSyncHandle;

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
        AgentQuestSyncHandle quest = mock(AgentQuestSyncHandle.class);

        assertEquals(2100, AgentPartyQuestSyncService.resolveQuestNpc(source, quest, 2100));

        when(quest.npc(source)).thenReturn(2200);
        assertEquals(2200, AgentPartyQuestSyncService.resolveQuestNpc(source, quest, 0));

        when(quest.npc(source)).thenReturn(0);
        assertEquals(constants.id.NpcId.MAPLE_ADMINISTRATOR,
                AgentPartyQuestSyncService.resolveQuestNpc(source, quest, 0));
    }

    @Test
    void startsQuestForPartyAgentsThatAreNotStarted() {
        Character source = character(1, mock(Client.class));
        Character agent = character(2, new BotClient(0, 0));
        AgentQuestSyncHandle quest = mock(AgentQuestSyncHandle.class);
        AgentQuestSyncGateway quests = questGateway(1000, quest);
        when(source.getParty()).thenReturn(mock(Party.class));
        when(source.getPartyMembersOnline()).thenReturn(List.of(agent));
        when(quest.npc(source)).thenReturn(3000);
        when(quest.status(agent)).thenReturn(QuestStatus.Status.NOT_STARTED);

        AgentPartyQuestSyncService.syncPartyAgentsQuestStart(source, 1000, 0, quests);

        verify(quest).forceStartWithActions(agent, 3000);
    }

    @Test
    void doesNotRestartAlreadyStartedPartyAgents() {
        Character source = character(1, mock(Client.class));
        Character agent = character(2, new BotClient(0, 0));
        AgentQuestSyncHandle quest = mock(AgentQuestSyncHandle.class);
        AgentQuestSyncGateway quests = questGateway(1000, quest);
        when(source.getParty()).thenReturn(mock(Party.class));
        when(source.getPartyMembersOnline()).thenReturn(List.of(agent));
        when(quest.status(agent)).thenReturn(QuestStatus.Status.STARTED);

        AgentPartyQuestSyncService.syncPartyAgentsQuestStart(source, 1000, 2000, quests);

        verify(quest, never()).forceStartWithActions(agent, 2000);
    }

    private static AgentQuestSyncGateway questGateway(int questId, AgentQuestSyncHandle quest) {
        AgentQuestSyncGateway quests = mock(AgentQuestSyncGateway.class);
        when(quests.getQuest(questId)).thenReturn(quest);
        return quests;
    }

    private static Character character(int id, Client client) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getClient()).thenReturn(client);
        return character;
    }
}
