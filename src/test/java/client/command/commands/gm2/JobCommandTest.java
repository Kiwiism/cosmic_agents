package client.command.commands.gm2;

import client.Character;
import client.Client;
import client.Job;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobCommandTest {
    @Test
    void usesAdministrativeOverrideInsteadOfPlayerAdvancement() {
        Client client = mock(Client.class);
        Character player = mock(Character.class);
        when(client.getPlayer()).thenReturn(player);
        when(player.forceChangeJobForAdmin(Job.HUNTER)).thenReturn(true);

        new JobCommand().execute(client, new String[]{String.valueOf(Job.HUNTER.getId())});

        verify(player).forceChangeJobForAdmin(Job.HUNTER);
        verify(player).equipChanged();
    }
}
