package server.agents.integration;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentCombatStanceGatewayTest {
    @Test
    void broadcastsCurrentStanceForLiveAgent() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        AgentCombatStanceGateway.broadcastCurrentStance(entry);

        verify(agent).broadcastStance();
    }

    @Test
    void ignoresMissingAgent() {
        AgentCombatStanceGateway.broadcastCurrentStance(new AgentRuntimeEntry(null, null, null));
    }
}
