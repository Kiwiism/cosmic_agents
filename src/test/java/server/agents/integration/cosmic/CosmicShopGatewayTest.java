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
}
