package server.agents.integration;

import org.junit.jupiter.api.Test;
import server.agents.integration.cosmic.CosmicAgentServerAdapter;
import server.agents.integration.cosmic.CosmicCombatGateway;
import server.agents.integration.cosmic.CosmicInventoryGateway;
import server.agents.integration.cosmic.CosmicMapGateway;
import server.agents.integration.cosmic.CosmicPacketGateway;
import server.agents.integration.cosmic.CosmicTradeGateway;

import static org.junit.jupiter.api.Assertions.assertSame;

class CosmicAgentServerAdapterTest {
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
    void exposesCosmicTradeGateway() {
        assertSame(CosmicTradeGateway.INSTANCE, CosmicAgentServerAdapter.INSTANCE.trade());
    }
}
