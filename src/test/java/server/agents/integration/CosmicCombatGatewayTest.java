package server.agents.integration;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.cosmic.CosmicCombatGateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CosmicCombatGatewayTest {
    @Test
    void rejectsMissingAgentClientOrPacket() {
        Character agent = mock(Character.class);
        when(agent.getClient()).thenReturn(null);

        assertFalse(CosmicCombatGateway.INSTANCE.dispatchSyntheticPacket(null, new byte[] {1}));
        assertFalse(CosmicCombatGateway.INSTANCE.dispatchSyntheticPacket(agent, null));
        assertFalse(CosmicCombatGateway.INSTANCE.dispatchSyntheticPacket(agent, new byte[] {1}));
    }
}
