package server.agents.capabilities.inventory;

import client.inventory.WeaponType;
import constants.id.ItemId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
