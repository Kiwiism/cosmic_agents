package net.server;

import client.BotClient;
import client.Character;
import client.Client;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerStorageTest {
    @Test
    void disconnectAllClearsBothCharacterIndexes() {
        PlayerStorage storage = new PlayerStorage();
        Character character = mock(Character.class);
        Client client = mock(Client.class);
        when(character.getId()).thenReturn(12);
        when(character.getName()).thenReturn("Alice");
        when(character.getClient()).thenReturn(client);
        storage.addPlayer(character);

        storage.disconnectAll();

        verify(client).forceDisconnect();
        assertNull(storage.getCharacterById(12));
        assertNull(storage.getCharacterByName("Alice"));
    }

    @Test
    void separatesRealPlayersAgentsAndNetworkRecipients() {
        PlayerStorage storage = new PlayerStorage();
        Character realPlayer = character(1, "player", mock(Client.class));
        Character agent = character(2, "agent", mock(BotClient.class));
        Character detachedRealPlayer = character(3, "detached", null);

        storage.addPlayer(realPlayer);
        storage.addPlayer(agent);
        storage.addPlayer(detachedRealPlayer);

        assertEquals(3, storage.getSize());
        assertEquals(2, storage.getRealPlayerCount());
        assertEquals(1, storage.getAgentCount());
        assertEquals(java.util.List.of(realPlayer, detachedRealPlayer), storage.getRealPlayers());
        assertEquals(java.util.List.of(realPlayer), storage.getNetworkRecipients());
        assertEquals(java.util.List.of(agent), storage.getAgents());
    }

    @Test
    void reusesImmutableSnapshotUntilMembershipChanges() {
        PlayerStorage storage = new PlayerStorage();
        Character first = character(1, "first", mock(Client.class));
        Character second = character(2, "second", mock(Client.class));
        storage.addPlayer(first);

        Collection<Character> before = storage.getAllCharacters();
        assertSame(before, storage.getAllCharacters());
        assertThrows(UnsupportedOperationException.class, () -> before.clear());

        storage.addPlayer(second);
        Collection<Character> afterAdd = storage.getAllCharacters();
        assertEquals(java.util.List.of(first, second), afterAdd);

        storage.removePlayer(first.getId());
        assertEquals(java.util.List.of(second), storage.getAllCharacters());
    }

    private static Character character(int id, String name, Client client) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        when(character.getClient()).thenReturn(client);
        return character;
    }
}
