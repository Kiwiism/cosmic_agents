package server.agents.integration;

import org.junit.jupiter.api.Test;
import server.agents.integration.cosmic.CosmicAgentServerAdapter;
import server.agents.integration.cosmic.CosmicCombatGateway;
import server.agents.integration.cosmic.CosmicPacketGateway;

import static org.junit.jupiter.api.Assertions.assertSame;

class CosmicAgentServerAdapterTest {
    @Test
    void exposesCosmicPacketGateway() {
        assertSame(CosmicPacketGateway.INSTANCE, CosmicAgentServerAdapter.INSTANCE.packets());
    }

    @Test
    void exposesCosmicCombatGateway() {
        assertSame(CosmicCombatGateway.INSTANCE, CosmicAgentServerAdapter.INSTANCE.combat());
    }
}
