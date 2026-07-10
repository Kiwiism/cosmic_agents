package server;

import client.Client;
import org.junit.jupiter.api.Test;
import tools.PacketCreator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ShopValidationTest {
    @Test
    void shouldRejectRechargeOnlyPurchase() {
        Shop shop = new Shop(1, 10000);
        shop.addItem(new ShopItem((short) 1000, 2070006, 0, 0));
        Client client = mock(Client.class);

        shop.buy(client, (short) 0, 2070006, (short) 1);

        verify(client).sendPacket(PacketCreator.shopTransaction((byte) 0x06));
    }

    @Test
    void shouldRejectInvalidSlotWithoutIndexingList() {
        Shop shop = new Shop(1, 10000);
        Client client = mock(Client.class);

        shop.buy(client, Short.MAX_VALUE, 2070006, (short) 1);

        verify(client).sendPacket(PacketCreator.shopTransaction((byte) 0x06));
    }

    @Test
    void shouldRejectOverflowingCost() {
        assertNull(Shop.checkedCost(Integer.MAX_VALUE, (short) 2));
        assertEquals(1_000, Shop.checkedCost(100, (short) 10));
    }
}
