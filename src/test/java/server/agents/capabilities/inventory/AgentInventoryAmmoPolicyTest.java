package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import constants.id.ItemId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentInventoryAmmoPolicyTest {
    @Test
    void shouldMatchAmmoToLegacyWeaponTypes() {
        assertTrue(AgentInventoryAmmoPolicy.isAmmoForWeapon(2060000, WeaponType.BOW));
        assertTrue(AgentInventoryAmmoPolicy.isAmmoForWeapon(2061000, WeaponType.CROSSBOW));
        assertTrue(AgentInventoryAmmoPolicy.isAmmoForWeapon(ItemId.SUBI_THROWING_STARS, WeaponType.CLAW));
        assertTrue(AgentInventoryAmmoPolicy.isAmmoForWeapon(ItemId.BULLET, WeaponType.GUN));

        assertFalse(AgentInventoryAmmoPolicy.isAmmoForWeapon(2061000, WeaponType.BOW));
        assertFalse(AgentInventoryAmmoPolicy.isAmmoForWeapon(ItemId.SUBI_THROWING_STARS, WeaponType.GUN));
        assertFalse(AgentInventoryAmmoPolicy.isAmmoForWeapon(2000000, WeaponType.SWORD1H));
    }

    @Test
    void shouldResolveTradeAmmoWeaponTypeLikeLegacyInventory() {
        assertEquals(WeaponType.BOW, AgentInventoryAmmoPolicy.ammoWeaponType(2060000));
        assertEquals(WeaponType.CROSSBOW, AgentInventoryAmmoPolicy.ammoWeaponType(2061000));
        assertEquals(WeaponType.CLAW, AgentInventoryAmmoPolicy.ammoWeaponType(ItemId.SUBI_THROWING_STARS));
        assertEquals(WeaponType.GUN, AgentInventoryAmmoPolicy.ammoWeaponType(ItemId.BULLET));
        assertNull(AgentInventoryAmmoPolicy.ammoWeaponType(2000000));

        assertTrue(AgentInventoryAmmoPolicy.isTradeAmmoItem(2060000));
        assertFalse(AgentInventoryAmmoPolicy.isTradeAmmoItem(2000000));
    }

    @Test
    void shouldAllowTradeAmmoOnlyForProjectileWeaponTypes() {
        assertEquals(WeaponType.BOW, AgentInventoryAmmoPolicy.tradeAmmoWeaponType(WeaponType.BOW));
        assertEquals(WeaponType.CROSSBOW, AgentInventoryAmmoPolicy.tradeAmmoWeaponType(WeaponType.CROSSBOW));
        assertEquals(WeaponType.CLAW, AgentInventoryAmmoPolicy.tradeAmmoWeaponType(WeaponType.CLAW));
        assertEquals(WeaponType.GUN, AgentInventoryAmmoPolicy.tradeAmmoWeaponType(WeaponType.GUN));
        assertNull(AgentInventoryAmmoPolicy.tradeAmmoWeaponType(WeaponType.SWORD1H));
    }

    @Test
    void shouldCollectMatchingAmmoStacksByProjectileWatkThenItemId() {
        Character donor = mock(Character.class);
        Inventory use = mock(Inventory.class);
        Item strongArrow = item(2060002, 10);
        Item crossbowArrow = item(2061000, 10);
        Item weakArrowHighId = item(2060001, 10);
        Item weakArrowLowId = item(2060000, 10);
        when(donor.getInventory(InventoryType.USE)).thenReturn(use);
        when(use.getSlotLimit()).thenReturn((byte) 4);
        when(use.getItem((short) 1)).thenReturn(strongArrow);
        when(use.getItem((short) 2)).thenReturn(crossbowArrow);
        when(use.getItem((short) 3)).thenReturn(weakArrowHighId);
        when(use.getItem((short) 4)).thenReturn(weakArrowLowId);

        List<Item> items = AgentInventoryAmmoPolicy.collectShareItems(donor, WeaponType.BOW, 99,
                itemId -> itemId == 2060002 ? 2 : 1);

        assertEquals(List.of(weakArrowLowId, weakArrowHighId, strongArrow), items);
    }

    @Test
    void shouldStopCollectingAmmoStacksAtShareQuantityBudget() {
        Character donor = mock(Character.class);
        Inventory use = mock(Inventory.class);
        Item first = item(2060000, 6);
        Item second = item(2060001, 6);
        when(donor.getInventory(InventoryType.USE)).thenReturn(use);
        when(use.getSlotLimit()).thenReturn((byte) 2);
        when(use.getItem((short) 1)).thenReturn(first);
        when(use.getItem((short) 2)).thenReturn(second);

        List<Item> items = AgentInventoryAmmoPolicy.collectShareItems(donor, WeaponType.BOW, 6,
                itemId -> 1);

        assertEquals(List.of(first), items);
    }

    private static Item item(int itemId, int quantity) {
        Item item = mock(Item.class);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getQuantity()).thenReturn((short) quantity);
        return item;
    }
}
