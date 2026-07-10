package net.server;

import client.Character;
import client.Client;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
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
}
