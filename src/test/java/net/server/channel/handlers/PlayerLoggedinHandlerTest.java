package net.server.channel.handlers;

import client.Client;
import org.junit.jupiter.api.Test;
import tools.PacketCreator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerLoggedinHandlerTest {
    @Test
    void shouldRejectLoginWhenClientLockCannotBeAcquired() {
        Client client = mock(Client.class);
        when(client.tryacquireClient()).thenReturn(false);

        assertFalse(PlayerLoggedinHandler.acquireClientForLogin(client));

        verify(client).sendPacket(PacketCreator.getAfterLoginError(10));
    }

    @Test
    void shouldContinueWhenClientLockIsAcquired() {
        Client client = mock(Client.class);
        when(client.tryacquireClient()).thenReturn(true);

        assertTrue(PlayerLoggedinHandler.acquireClientForLogin(client));
    }
}
