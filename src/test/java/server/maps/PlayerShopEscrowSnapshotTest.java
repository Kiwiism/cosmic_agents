package server.maps;

import client.Character;
import client.inventory.Equip;
import client.inventory.Item;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerShopEscrowSnapshotTest {
    @Test
    void roundTripsCompleteEquipmentAndListingTerms() {
        Character owner = mock(Character.class);
        when(owner.getId()).thenReturn(77);
        when(owner.getMapId()).thenReturn(910000001);
        when(owner.getPosition()).thenReturn(new Point(30, 0));
        PlayerShop shop = new PlayerShop(owner, "real drops", 5140000);
        shop.enableDurableEscrow("94ad93db-6f57-4662-ac49-60cfc7c21568");
        shop.setPosition(new Point(120, 0));
        Equip equip = Equip.restored(1002001, (short) 1);
        equip.setStr((short) 4);
        equip.setDex((short) 7);
        equip.setWatk((short) 2);
        equip.setUpgradeSlots((byte) 5);
        equip.setOwner("maker");
        assertTrue(shop.addItem(new PlayerShopItem(equip, (short) 1, 12_345)));

        PlayerShopEscrowSnapshot captured = PlayerShopEscrowSnapshot.capture(shop);
        List<PlayerShopEscrowSnapshot.Listing> decoded =
                PlayerShopEscrowSnapshot.decodeListings(captured.listingsJson());
        Item restored = decoded.getFirst().toPlayerShopItem().getItem();

        assertInstanceOf(Equip.class, restored);
        assertEquals(4, ((Equip) restored).getStr());
        assertEquals(7, ((Equip) restored).getDex());
        assertEquals(5, ((Equip) restored).getUpgradeSlots());
        assertEquals(12_345, decoded.getFirst().price());
        assertEquals(120, captured.spotX());
    }
}
