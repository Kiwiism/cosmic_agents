package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import client.Client;
import net.packet.Packet;
import net.server.world.Party;
import net.server.world.World;
import org.junit.jupiter.api.Test;
import server.agents.commands.AgentReplyChannelStateRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.maps.MapleMap;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        when(map.isObservedByPlayer()).thenReturn(true);

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
        when(map.isObservedByPlayer()).thenReturn(true);

        AgentReplyRuntime.sayPartyNow(bot, "party");

        verify(map).broadcastMessage(any(Packet.class));
    }

    @Test
    void privateStatusUsesPartyWhenAgentAndOwnerShareOne() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        Client botClient = mock(Client.class);
        Party party = mock(Party.class);
        World world = mock(World.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);
        when(bot.getParty()).thenReturn(party);
        when(owner.getParty()).thenReturn(party);
        when(party.getId()).thenReturn(7);
        when(party.getMembers()).thenReturn(List.of());
        when(bot.getClient()).thenReturn(botClient);
        when(botClient.getWorldServer()).thenReturn(world);
        when(bot.getName()).thenReturn("agent");

        AgentReplyRuntime.sayPartyOrWhisperNow(entry, owner, "getting ready");

        verify(world).partyChat(party, "getting ready", "agent");
        verify(owner, never()).sendPacket(any(Packet.class));
    }

    @Test
    void privateStatusWhispersWhenPartyChatIsUnavailable() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        Client botClient = mock(Client.class);
        Client ownerClient = mock(Client.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);
        when(bot.getName()).thenReturn("agent");
        when(bot.getClient()).thenReturn(botClient);
        when(owner.getClient()).thenReturn(ownerClient);
        when(botClient.getChannel()).thenReturn(2);

        AgentReplyRuntime.sayPartyOrWhisperNow(entry, owner, "ready");

        verify(owner).sendPacket(any(Packet.class));
    }

    @Test
    void visibleSaySkipsBroadcastWhenMapHasNoPlayerObserver() {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        when(bot.getMap()).thenReturn(map);

        AgentReplyRuntime.visibleSayNow(entry, "hello");

        verify(map, never()).broadcastMessage(any(Packet.class));
    }
}
