package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import client.Client;
import net.packet.Packet;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentReplyChannelStateRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.maps.MapleMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentReplyRuntimeTest {
    @Test
    void visibleSayBroadcastsToMap() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);

        when(bot.getMap()).thenReturn(map);
        when(bot.getId()).thenReturn(7);

        AgentReplyRuntime.visibleSayNow(entry, "hello");

        verify(map).broadcastMessage(any(Packet.class));
    }

    @Test
    void whisperReplySendsPacketToOwner() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        Client botClient = mock(Client.class);
        Client ownerClient = mock(Client.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);
        AgentReplyChannelStateRuntime.setWhisper(entry);

        when(bot.getName()).thenReturn("agent");
        when(bot.getClient()).thenReturn(botClient);
        when(botClient.getChannel()).thenReturn(1);
        when(owner.getClient()).thenReturn(ownerClient);

        AgentReplyRuntime.replyNow(entry, "ok");

        verify(owner).sendPacket(any(Packet.class));
    }

    @Test
    void partyDeliveryFallsBackToMapWhenBotHasNoParty() {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);

        when(bot.getParty()).thenReturn(null);
        when(bot.getMap()).thenReturn(map);
        when(bot.getId()).thenReturn(7);

        AgentReplyRuntime.sayPartyNow(bot, "party");

        verify(map).broadcastMessage(any(Packet.class));
    }
}
