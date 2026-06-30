package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.bots.BotEntry;
import server.maps.MapleMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTradeRecipientServiceTest {
    @Test
    void resolvesOwnerWhenNoExplicitRecipientIsSet() {
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        BotEntry entry = new BotEntry(agent, owner, null);

        assertSame(owner, AgentTradeRecipientService.resolveTradeRecipient(entry, agent));
    }

    @Test
    void resolvesExplicitOwnerRecipient() {
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        BotEntry entry = new BotEntry(agent, owner, null);
        when(owner.getId()).thenReturn(42);
        AgentBotPendingTradeStateRuntime.setRecipientId(entry, 42);

        assertSame(owner, AgentTradeRecipientService.resolveTradeRecipient(entry, agent));
    }

    @Test
    void resolvesMapRecipientBeforePartyScan() {
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        Character mapRecipient = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        BotEntry entry = new BotEntry(agent, owner, null);
        when(agent.getMap()).thenReturn(map);
        when(map.getCharacterById(99)).thenReturn(mapRecipient);
        AgentBotPendingTradeStateRuntime.setRecipientId(entry, 99);

        assertSame(mapRecipient, AgentTradeRecipientService.resolveTradeRecipient(entry, agent));
    }

    @Test
    void resolvesOnlinePartyRecipientWhenNotOnMap() {
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        Character partyRecipient = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        BotEntry entry = new BotEntry(agent, owner, null);
        when(agent.getMap()).thenReturn(map);
        when(owner.getParty()).thenReturn(mock(net.server.world.Party.class));
        when(owner.getPartyMembersOnline()).thenReturn(List.of(partyRecipient));
        when(partyRecipient.getId()).thenReturn(77);
        AgentBotPendingTradeStateRuntime.setRecipientId(entry, 77);

        assertSame(partyRecipient, AgentTradeRecipientService.resolveTradeRecipient(entry, agent));
    }

    @Test
    void returnsNullWhenExplicitRecipientCannotBeFound() {
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        BotEntry entry = new BotEntry(agent, owner, null);
        AgentBotPendingTradeStateRuntime.setRecipientId(entry, 123);

        assertNull(AgentTradeRecipientService.resolveTradeRecipient(entry, agent));
    }
}
