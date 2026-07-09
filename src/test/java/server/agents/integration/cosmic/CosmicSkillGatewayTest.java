package server.agents.integration.cosmic;

import org.junit.jupiter.api.Test;
import server.agents.integration.SkillGateway;

import static org.junit.jupiter.api.Assertions.assertSame;

class CosmicSkillGatewayTest {
    @Test
    void adapterExposesSkillGateway() {
        SkillGateway gateway = CosmicAgentServerAdapter.INSTANCE.skills();

        assertSame(CosmicSkillGateway.INSTANCE, gateway);
    }
}
