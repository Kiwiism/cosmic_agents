package server.agents.integration.cosmic;

import client.Character;
import client.Client;
import net.server.world.Party;
import net.server.world.PartyCharacter;
import net.server.world.World;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentPartySnapshot;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void snapshotsPartyIdentityAndOrderedMembers() {
        Character character = mock(Character.class);
        Party party = mock(Party.class);
        PartyCharacter first = mock(PartyCharacter.class);
        PartyCharacter second = mock(PartyCharacter.class);
        when(character.getParty()).thenReturn(party);
        when(party.getId()).thenReturn(42);
        when(party.getMembers()).thenReturn(List.of(first, second));
        when(first.getId()).thenReturn(1);
        when(first.getName()).thenReturn("Leader");
        when(first.isLeader()).thenReturn(true);
        when(first.getMapId()).thenReturn(100000000);
        when(second.getId()).thenReturn(2);
        when(second.getName()).thenReturn("Agent");
        when(second.getMapId()).thenReturn(100000001);

        AgentPartySnapshot snapshot = CosmicPartyGateway.INSTANCE.snapshot(character);

        assertEquals(42, snapshot.id());
        assertEquals(List.of("Leader", "Agent"), snapshot.members().stream().map(member -> member.name()).toList());
        assertTrue(snapshot.members().get(0).leader());
        assertEquals(100000001, snapshot.members().get(1).mapId());
    }
}
