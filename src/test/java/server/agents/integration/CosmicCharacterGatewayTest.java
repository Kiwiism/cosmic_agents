package server.agents.integration;

import client.Character;
import client.Client;
import org.junit.jupiter.api.Test;
import server.agents.integration.cosmic.CosmicCharacterGateway;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CosmicCharacterGatewayTest {
    @Test
    void markClientHeartbeatUpdatesLastPacketWhenClientExists() {
        Character agent = mock(Character.class);
        Client client = mock(Client.class);
        when(agent.getClient()).thenReturn(client);

        CosmicCharacterGateway.INSTANCE.markClientHeartbeat(agent);

        verify(client).updateLastPacket();
    }

    @Test
    void markClientHeartbeatIgnoresMissingClient() {
        Character agent = mock(Character.class);
        when(agent.getClient()).thenReturn(null);
        Client client = mock(Client.class);

        CosmicCharacterGateway.INSTANCE.markClientHeartbeat(agent);

        verify(client, never()).updateLastPacket();
    }
}
