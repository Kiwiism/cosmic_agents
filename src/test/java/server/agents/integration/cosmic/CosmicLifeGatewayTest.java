package server.agents.integration.cosmic;

import org.junit.jupiter.api.Test;
import server.agents.integration.LifeGateway;

import static org.junit.jupiter.api.Assertions.assertSame;

class CosmicLifeGatewayTest {
    @Test
    void adapterExposesLifeGateway() {
        LifeGateway gateway = CosmicAgentServerAdapter.INSTANCE.life();

        assertSame(CosmicLifeGateway.INSTANCE, gateway);
    }
}
