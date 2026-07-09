package server.agents.capabilities.shop;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentShopGatewayBoundaryTest {
    @Test
    void shopSellClientMutationLivesInCosmicGateway() throws IOException {
        String service = Files.readString(Path.of(
                "src/main/java/server/agents/capabilities/shop/AgentShopService.java"));
        String gateway = Files.readString(Path.of(
                "src/main/java/server/agents/integration/cosmic/CosmicShopGateway.java"));

        assertFalse(service.contains("shop.sell(bot.getClient()"));
        assertTrue(service.contains("AgentShopGatewayRuntime.shop().sell("));
        assertTrue(gateway.contains("shop.sell(agent.getClient(), type, slot, quantity)"));
    }
}
