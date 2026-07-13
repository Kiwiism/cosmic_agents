package net.server.channel.handlers;

import client.Character;
import client.Client;
import net.packet.InPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentEquipMailboxBoundaryTest {
    @AfterEach
    void cleanUp() {
        System.clearProperty("agents.mailbox.enabled");
        AgentRuntimeRegistry.clear();
    }

    @Test
    void packetThreadOnlyEnqueuesMutatingEquipmentRequest() {
        System.setProperty("agents.mailbox.enabled", "true");
        Character player = mock(Character.class);
        Character agent = mock(Character.class);
        Client client = mock(Client.class);
        InPacket packet = mock(InPacket.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, player, null);
        when(player.getId()).thenReturn(10);
        when(client.getPlayer()).thenReturn(player);
        when(packet.readByte()).thenReturn((byte) 5, (byte) 1);
        when(packet.readShort()).thenReturn((short) 1);
        AgentRuntimeRegistry.registerEntry(player.getId(), entry);

        new AgentEquipHandler().handlePacket(packet, client);

        assertEquals(1, entry.actionMailbox().size());
        verify(client, never()).sendPacket(any());
        entry.actionMailbox().close();
    }
}
