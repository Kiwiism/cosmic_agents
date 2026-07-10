package net.server.world;

import client.Character;
import client.Client;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessengerLifecycleTest {
    @Test
    void memberViewIsSnapshotAndEmptyStateTracksRemoval() {
        MessengerCharacter first = member("First", 1, 0);
        Messenger messenger = new Messenger(10, first);
        Collection<MessengerCharacter> snapshot = messenger.getMembers();

        MessengerCharacter second = member("Second", 2, 1);
        messenger.addMember(second, 1);

        assertEquals(1, snapshot.size());
        assertEquals(2, messenger.getMembers().size());
        assertFalse(messenger.isEmpty());

        messenger.removeMember(first);
        messenger.removeMember(second);
        assertTrue(messenger.isEmpty());
    }

    private static MessengerCharacter member(String name, int id, int position) {
        Character character = mock(Character.class);
        Client client = mock(Client.class);
        when(character.getName()).thenReturn(name);
        when(character.getId()).thenReturn(id);
        when(character.getClient()).thenReturn(client);
        when(client.getChannel()).thenReturn(1);
        return new MessengerCharacter(character, position);
    }
}
