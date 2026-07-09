package server.agents.integration.cosmic;

import client.Character;
import org.junit.jupiter.api.Test;
import server.Shop;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CosmicShopGatewayTest {
    @Test
    void delegatesRechargeToDirectShopTransaction() {
        Character agent = mock(Character.class);
        Shop shop = mock(Shop.class);
        short slot = 4;
        when(shop.rechargeDirect(agent, slot)).thenReturn(Shop.TransactionResult.NO_SPACE);

        Shop.TransactionResult result = CosmicShopGateway.INSTANCE.recharge(agent, shop, slot);

        assertSame(Shop.TransactionResult.NO_SPACE, result);
        verify(shop).rechargeDirect(agent, slot);
    }

    @Test
    void delegatesBuyToDirectShopTransaction() {
        Character agent = mock(Character.class);
        Shop shop = mock(Shop.class);
        short slot = 3;
        int itemId = 2000000;
        short quantity = 25;
        when(shop.buyDirect(agent, slot, itemId, quantity)).thenReturn(Shop.TransactionResult.NOT_ENOUGH_MESO);

        Shop.TransactionResult result = CosmicShopGateway.INSTANCE.buy(agent, shop, slot, itemId, quantity);

        assertSame(Shop.TransactionResult.NOT_ENOUGH_MESO, result);
        verify(shop).buyDirect(agent, slot, itemId, quantity);
    }
}
