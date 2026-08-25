package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import server.agents.economy.market.FreeMarketPhysicalGateway;
import server.maps.PlayerShop;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CosmicMarketSellerGatewayTest {
    private final int permit = 5140000;
    private final CosmicMarketSellerGateway gateway = new CosmicMarketSellerGateway(
            mock(RemoteNpcCommerceService.class), permit, 30_000);

    @Test
    void recognizesAlreadyOpenOwnedShop() {
        Character agent = mock(Character.class);
        PlayerShop shop = mock(PlayerShop.class);
        when(agent.getPlayerShop()).thenReturn(shop);
        when(shop.isOwner(agent)).thenReturn(true);
        when(shop.isOpen()).thenReturn(true);

        assertEquals(FreeMarketPhysicalGateway.ActionStatus.ARRIVED,
                gateway.requestOpen(agent, plan(910000001)));
    }

    @Test
    void requiresPreferredRoomAndLegitimateOwnedPermit() {
        Character agent = mock(Character.class);
        Inventory cash = mock(Inventory.class);
        when(agent.getInventory(InventoryType.CASH)).thenReturn(cash);
        when(agent.getMapId()).thenReturn(910000002);
        when(cash.countById(permit)).thenReturn(1);

        assertEquals(FreeMarketPhysicalGateway.ActionStatus.UNAVAILABLE,
                gateway.requestOpen(agent, plan(910000001)));

        when(agent.getMapId()).thenReturn(910000001);
        when(cash.countById(permit)).thenReturn(0);
        assertFalse(gateway.hasPlayerShopPermit(agent));
        assertEquals(FreeMarketPhysicalGateway.ActionStatus.UNAVAILABLE,
                gateway.requestOpen(agent, plan(910000001)));
    }

    @Test
    void acceptsAnyConfiguredRealPermit() {
        CosmicMarketSellerGateway pooled = new CosmicMarketSellerGateway(
                mock(RemoteNpcCommerceService.class), List.of(5140000, 5140006), 30_000);
        Character agent = mock(Character.class);
        Inventory cash = mock(Inventory.class);
        when(agent.getInventory(InventoryType.CASH)).thenReturn(cash);
        when(cash.countById(5140006)).thenReturn(1);

        assertTrue(pooled.hasPlayerShopPermit(agent));
    }

    private static MarketSellerPlan plan(int room) {
        return new MarketSellerPlan(List.of(), List.of(), room, "test stall");
    }
}
