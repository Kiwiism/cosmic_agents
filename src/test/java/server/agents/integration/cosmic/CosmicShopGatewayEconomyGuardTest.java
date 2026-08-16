package server.agents.integration.cosmic;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.Shop;
import server.agents.integration.AgentEconomicActionGuardRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CosmicShopGatewayEconomyGuardTest {
    @AfterEach void reset() { AgentEconomicActionGuardRuntime.clear(); }

    @Test
    void deniedSaleNeverReachesRealCosmicShopMutation() {
        Character agent = mock(Character.class);
        Inventory inventory = mock(Inventory.class);
        Item item = new Item(4000000, (short) 2, (short) 3);
        Shop shop = mock(Shop.class);
        when(agent.getInventory(InventoryType.ETC)).thenReturn(inventory);
        when(inventory.getItem((short) 2)).thenReturn(item);
        AgentEconomicActionGuardRuntime.install((ignoredAgent, type, slot, itemId, quantity, venue, at) ->
                AgentEconomicActionGuardRuntime.Decision.denied("PROTECTED_UNREVIEWED"));

        Shop.TransactionResult result = CosmicShopGateway.INSTANCE.sell(
                agent, shop, InventoryType.ETC, (short) 2, (short) 1);

        assertEquals(Shop.TransactionResult.INVALID, result);
        verify(shop, never()).sellDirect(any(), any(), anyShort(), anyShort());
    }
}
