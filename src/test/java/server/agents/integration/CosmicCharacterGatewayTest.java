package server.agents.integration;

import client.Character;
import client.Client;
import client.BotClient;
import org.junit.jupiter.api.Test;
import server.agents.integration.cosmic.CosmicCharacterGateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void disconnectDelegatesToClientWhenClientExists() {
        Character agent = mock(Character.class);
        Client client = mock(Client.class);
        when(agent.getClient()).thenReturn(client);

        CosmicCharacterGateway.INSTANCE.disconnect(agent, false, false);

        verify(client).disconnect(false, false);
    }

    @Test
    void saveDelegatesToCharacterPersistence() {
        Character agent = mock(Character.class);

        CosmicCharacterGateway.INSTANCE.save(agent, true);

        verify(agent).saveCharToDB(true);
    }

    @Test
    void disconnectIgnoresMissingClient() {
        Character agent = mock(Character.class);
        when(agent.getClient()).thenReturn(null);
        Client client = mock(Client.class);

        CosmicCharacterGateway.INSTANCE.disconnect(agent, false, false);

        verify(client, never()).disconnect(false, false);
    }

    @Test
    void classifiesOnlyCharactersBackedByBotClientAsAgents() {
        Character agent = mock(Character.class);
        Character player = mock(Character.class);
        when(agent.getClient()).thenReturn(mock(BotClient.class));
        when(player.getClient()).thenReturn(mock(Client.class));

        assertTrue(CosmicCharacterGateway.INSTANCE.isAgentCharacter(agent));
        assertFalse(CosmicCharacterGateway.INSTANCE.isAgentCharacter(player));
        assertFalse(CosmicCharacterGateway.INSTANCE.isAgentCharacter(null));
    }
}
