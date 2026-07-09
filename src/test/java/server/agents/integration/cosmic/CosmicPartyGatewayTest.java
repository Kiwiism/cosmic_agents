package server.agents.integration.cosmic;

import client.Character;
import client.Client;
import net.server.world.Party;
import net.server.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CosmicPartyGatewayTest {
    @Test
    void reportsUnavailableWhenSpeakerHasNoParty() {
        Character speaker = mock(Character.class);

        assertFalse(CosmicPartyGateway.INSTANCE.sendPartyChat(speaker, "hello"));
    }

    @Test
    void sendsPartyChatThroughWorldServer() {
        Character speaker = mock(Character.class);
        Client client = mock(Client.class);
        World world = mock(World.class);
        Party party = mock(Party.class);
        when(speaker.getParty()).thenReturn(party);
        when(speaker.getClient()).thenReturn(client);
        when(speaker.getName()).thenReturn("Agent");
        when(client.getWorldServer()).thenReturn(world);

        assertTrue(CosmicPartyGateway.INSTANCE.sendPartyChat(speaker, "hello"));

        verify(world).partyChat(party, "hello", "Agent");
    }
}
