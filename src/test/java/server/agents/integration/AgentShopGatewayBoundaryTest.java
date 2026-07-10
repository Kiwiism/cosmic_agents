package server.agents.integration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentShopGatewayBoundaryTest {
    @Test
    void shopFactoryLookupRemainsInsideCosmicShopAdapter() throws Exception {
        String capability = Files.readString(Path.of(
                "src/main/java/server/agents/capabilities/shop/AgentShopService.java"));
        String cosmic = Files.readString(Path.of(
                "src/main/java/server/agents/integration/cosmic/CosmicShopGateway.java"));

        assertFalse(capability.contains("ShopFactory"));
        assertTrue(capability.contains("AgentShopGatewayRuntime.shop().findForNpc"));
        assertTrue(cosmic.contains("ShopFactory.getInstance().getShopForNPC"));
    }
}
