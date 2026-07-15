package server;

import client.Character;
import client.Client;
import org.junit.jupiter.api.Test;
import tools.PacketCreator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void shouldRejectDuplicateMedalWithPopupBeforeCharging() {
        Shop shop = new Shop(9000036, 9000036);
        shop.addItem(new ShopItem((short) 1000, 1142073, 1_000_000, 0));
        Client client = mock(Client.class);
        Character player = mock(Character.class);
        when(client.getPlayer()).thenReturn(player);
        when(player.haveItemWithId(1142073, true)).thenReturn(true);

        shop.buy(client, (short) 0, 1142073, (short) 1);

        verify(client).sendPacket(PacketCreator.shopTransaction((byte) 0));
        verify(client).sendPacket(PacketCreator.serverNotice(1, "You already have that medal."));
    }

    @Test
    void shouldRejectSaleProceedsThatDoNotFitMesoBalance() {
        Character player = mock(Character.class);
        when(player.canHoldMeso(50)).thenReturn(false);

        assertFalse(Shop.canReceiveSaleProceeds(player, 50));
        verify(player).canHoldMeso(50);
    }

    @Test
    void shouldRejectMesoOverflowSaleWithoutStockWarning() {
        Client client = mock(Client.class);

        Shop.rejectSaleForMesoCapacity(client);

        verify(client).sendPacket(PacketCreator.shopTransaction((byte) 0x8));
        verify(client).sendPacket(PacketCreator.serverNotice(1, "You cannot carry any more mesos."));
    }
}
