package server.bots;

import client.Character;
import client.Client;
import net.packet.Packet;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotReplyChannelStateRuntime;
import server.agents.integration.AgentBotReplyRuntime;
import server.maps.MapleMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentBotReplyRuntimeTest {
    @Test
    void visibleSayBroadcastsToMap() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        BotEntry entry = new BotEntry(bot, owner, null);

        when(bot.getMap()).thenReturn(map);
        when(bot.getId()).thenReturn(7);

        AgentBotReplyRuntime.visibleSayNow(entry, "hello");

        verify(map).broadcastMessage(any(Packet.class));
    }

    @Test
    void whisperReplySendsPacketToOwner() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        Client botClient = mock(Client.class);
        Client ownerClient = mock(Client.class);
        BotEntry entry = new BotEntry(bot, owner, null);
        AgentBotReplyChannelStateRuntime.setWhisper(entry);

        when(bot.getName()).thenReturn("agent");
        when(bot.getClient()).thenReturn(botClient);
        when(botClient.getChannel()).thenReturn(1);
        when(owner.getClient()).thenReturn(ownerClient);

        AgentBotReplyRuntime.replyNow(entry, "ok");

        verify(owner).sendPacket(any(Packet.class));
    }

    @Test
    void partyDeliveryFallsBackToMapWhenBotHasNoParty() {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);

        when(bot.getParty()).thenReturn(null);
        when(bot.getMap()).thenReturn(map);
        when(bot.getId()).thenReturn(7);

        AgentBotReplyRuntime.sayPartyNow(bot, "party");

        verify(map).broadcastMessage(any(Packet.class));
    }
}
