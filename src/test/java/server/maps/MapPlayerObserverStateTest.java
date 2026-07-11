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

        state.characterAdded(agent);
        assertFalse(state.isObserved());
        state.characterAdded(player);
        assertTrue(state.isObserved());
        assertEquals(1, state.count());
        state.characterRemoved(agent);
        assertTrue(state.isObserved());
        state.characterRemoved(player);
        assertFalse(state.isObserved());
        assertEquals(0, state.count());
    }
}
