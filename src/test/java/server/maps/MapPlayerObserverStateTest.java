package server.maps;

import client.BotClient;
import client.Character;
import client.Client;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MapPlayerObserverStateTest {
    @Test
    void countsRealClientsButNotHeadlessAgents() {
        Character player = mock(Character.class);
        when(player.getClient()).thenReturn(mock(Client.class));
        Character agent = mock(Character.class);
        when(agent.getClient()).thenReturn(mock(BotClient.class));
        MapPlayerObserverState state = new MapPlayerObserverState();

        assertFalse(state.characterAdded(agent));
        assertFalse(state.isObserved());
        assertTrue(state.characterAdded(player));
        assertTrue(state.isObserved());
        assertEquals(1, state.count());
        assertFalse(state.characterRemoved(agent));
        assertTrue(state.isObserved());
        assertTrue(state.characterRemoved(player));
        assertFalse(state.isObserved());
        assertEquals(0, state.count());
    }

    @Test
    void reportsOnlyZeroToOneAndOneToZeroTransitions() {
        Character first = mock(Character.class);
        when(first.getClient()).thenReturn(mock(Client.class));
        Character second = mock(Character.class);
        when(second.getClient()).thenReturn(mock(Client.class));
        MapPlayerObserverState state = new MapPlayerObserverState();

        assertTrue(state.characterAdded(first));
        assertFalse(state.characterAdded(second));
        assertFalse(state.characterRemoved(first));
        assertTrue(state.characterRemoved(second));
    }
}
