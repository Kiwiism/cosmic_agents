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
        assertFalse(service.contains("shop.rechargeDirect(bot"));
        assertFalse(service.contains("shop.buyDirect(bot"));
        assertTrue(service.contains("AgentShopGatewayRuntime.shop().sell("));
        assertTrue(service.contains("AgentShopGatewayRuntime.shop().recharge("));
        assertTrue(service.contains("AgentShopGatewayRuntime.shop()"));
        assertTrue(service.contains(".buy(bot, shop,"));
        assertTrue(gateway.contains("AgentEconomicActionGuardRuntime.claimNpcSale("));
        assertTrue(gateway.contains("shop.sellDirect(agent, type, slot, quantity)"));
        assertTrue(gateway.contains("shop.rechargeDirect(\n                agent, slot, minimumMesoReserve)"));
        assertTrue(gateway.contains("shop.buyDirect(agent, slot, itemId, quantity)"));
    }
}
