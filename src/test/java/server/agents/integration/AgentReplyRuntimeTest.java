package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import client.Client;
import net.packet.Packet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import config.YamlConfig;
import server.agents.commands.AgentReplyChannelStateRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.maps.MapleMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentReplyRuntimeTest {
    private boolean previousLegacyDialogue;

    @BeforeEach
    void enableLegacyDialogueForParityTests() {
        previousLegacyDialogue = YamlConfig.config.server.AGENT_LEGACY_DIALOGUE_ENABLED;
        YamlConfig.config.server.AGENT_LEGACY_DIALOGUE_ENABLED = true;
    }

    @AfterEach
    void restoreLegacyDialogueFlag() {
        YamlConfig.config.server.AGENT_LEGACY_DIALOGUE_ENABLED = previousLegacyDialogue;
    }

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
    void visibleSaySkipsBroadcastWhenMapHasNoPlayerObserver() {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        when(bot.getMap()).thenReturn(map);

        AgentReplyRuntime.visibleSayNow(entry, "hello");

        verify(map, never()).broadcastMessage(any(Packet.class));
    }

    @Test
    void legacyDialogueFlagSuppressesNuTNNuTReplies() {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        when(bot.getMap()).thenReturn(map);
        YamlConfig.config.server.AGENT_LEGACY_DIALOGUE_ENABLED = false;

        AgentReplyRuntime.queueSay(entry, "ambient");
        AgentReplyRuntime.visibleSayNow(entry, "ambient");

        verify(map, never()).broadcastMessage(any(Packet.class));
        org.junit.jupiter.api.Assertions.assertEquals(
                0, server.agents.commands.AgentMessageQueueStateRuntime.size(entry));
    }
}
