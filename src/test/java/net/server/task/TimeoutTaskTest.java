package net.server.task;

import client.BotClient;
import client.Character;
import client.Client;
import net.server.PlayerStorage;
import net.server.world.World;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TimeoutTaskTest {
    @Test
    void checksOnlyCharactersWithRealNetworkClients() {
        PlayerStorage storage = new PlayerStorage();
        Client realClient = mock(Client.class);
        when(realClient.getLastPacket()).thenReturn(System.currentTimeMillis());
        BotClient agentClient = mock(BotClient.class);
        Character realPlayer = character(1, "player", realClient);
        Character agent = character(2, "agent", agentClient);
        storage.addPlayer(realPlayer);
        storage.addPlayer(agent);
        World world = mock(World.class);
        when(world.getPlayerStorage()).thenReturn(storage);

        new TimeoutTask(world).run();

        verify(realClient).getLastPacket();
        verify(agentClient, never()).getLastPacket();
    }

    private static Character character(int id, String name, Client client) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        when(character.getClient()).thenReturn(client);
        return character;
    }
}
