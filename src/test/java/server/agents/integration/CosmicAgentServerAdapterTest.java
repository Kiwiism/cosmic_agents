package server.agents.integration;

import client.BotClient;
import client.Character;
import client.Client;
import org.junit.jupiter.api.Test;
import server.agents.integration.cosmic.CosmicAgentServerAdapter;
import server.agents.integration.cosmic.CosmicAgentClientGateway;
import server.agents.integration.cosmic.CosmicCharacterGateway;
import server.agents.integration.cosmic.CosmicCombatGateway;
import server.agents.integration.cosmic.CosmicInventoryGateway;
import server.agents.integration.cosmic.CosmicSchedulerGateway;
import server.agents.integration.cosmic.CosmicMapGateway;
import server.agents.integration.cosmic.CosmicPacketGateway;
import server.agents.integration.cosmic.CosmicPartyGateway;
import server.agents.integration.cosmic.CosmicAgentPersistenceGateway;
import server.agents.integration.cosmic.CosmicShopGateway;
import server.agents.integration.cosmic.CosmicTradeGateway;
import server.agents.integration.cosmic.CosmicPrimitiveCapabilityGateway;
import server.integration.AgentPresence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CosmicAgentServerAdapterTest {
    @Test
    void exposesCosmicAgentClientGateway() {
        assertSame(CosmicAgentClientGateway.INSTANCE, CosmicAgentServerAdapter.INSTANCE.agentClients());
    }

    @Test
    void exposesCosmicCharacterGateway() {
        assertSame(CosmicCharacterGateway.INSTANCE, CosmicAgentServerAdapter.INSTANCE.characters());
    }

    @Test
    void exposesCosmicMapGateway() {
        assertSame(CosmicMapGateway.INSTANCE, CosmicAgentServerAdapter.INSTANCE.maps());
    }

    @Test
    void exposesCosmicPacketGateway() {
        assertSame(CosmicPacketGateway.INSTANCE, CosmicAgentServerAdapter.INSTANCE.packets());
    }

    @Test
    void exposesCosmicCombatGateway() {
        assertSame(CosmicCombatGateway.INSTANCE, CosmicAgentServerAdapter.INSTANCE.combat());
    }

    @Test
    void exposesCosmicInventoryGateway() {
        assertSame(CosmicInventoryGateway.INSTANCE, CosmicAgentServerAdapter.INSTANCE.inventory());
    }

    @Test
    void exposesCosmicSchedulerGateway() {
        assertSame(CosmicSchedulerGateway.INSTANCE, CosmicAgentServerAdapter.INSTANCE.scheduler());
    }

    @Test
    void exposesCosmicShopGateway() {
        assertSame(CosmicShopGateway.INSTANCE, CosmicAgentServerAdapter.INSTANCE.shop());
    }

    @Test
    void exposesCosmicTradeGateway() {
        assertSame(CosmicTradeGateway.INSTANCE, CosmicAgentServerAdapter.INSTANCE.trade());
    }

    @Test
    void exposesCosmicPartyGateway() {
        assertSame(CosmicPartyGateway.INSTANCE, CosmicAgentServerAdapter.INSTANCE.party());
    }

    @Test
    void exposesCosmicPersistenceGateway() {
        assertSame(CosmicAgentPersistenceGateway.INSTANCE, CosmicAgentServerAdapter.INSTANCE.persistence());
    }

    @Test
    void exposesPrimitiveCapabilityGateway() {
        assertSame(CosmicPrimitiveCapabilityGateway.INSTANCE,
                CosmicAgentServerAdapter.INSTANCE.primitiveCapabilities());
    }

    @Test
    void installsBotClientAgentPresenceProvider() {
        CosmicAgentServerAdapter adapter = CosmicAgentServerAdapter.INSTANCE;
        Character agent = mock(Character.class);
        Character player = mock(Character.class);
        when(agent.getClient()).thenReturn(mock(BotClient.class));
        when(player.getClient()).thenReturn(mock(Client.class));

        assertSame(CosmicCharacterGateway.INSTANCE, adapter.characters());
        assertTrue(AgentPresence.isAgent(agent));
        assertFalse(AgentPresence.isAgent(player));
    }
}
