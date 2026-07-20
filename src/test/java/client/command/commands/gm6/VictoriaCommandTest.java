package client.command.commands.gm6;

import client.Character;
import client.Client;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VictoriaCommandTest {
    @Test
    void missingParametersPrintUsage() {
        Client client = mock(Client.class);
        Character player = mock(Character.class);
        when(client.getPlayer()).thenReturn(player);

        new VictoriaCommand().execute(client, new String[0]);

        verify(player).yellowMessage(contains("Usage: !victoria"));
    }
}
