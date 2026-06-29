package server.agents.capabilities.shop;

import client.inventory.Item;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentShopAmmoPolicyTest {
    @Test
    void shouldClassifyFixedAndRechargeAmmoWeapons() {
        assertTrue(AgentShopAmmoPolicy.needsFixedAmmoWeapon(WeaponType.BOW));
        assertTrue(AgentShopAmmoPolicy.needsFixedAmmoWeapon(WeaponType.CROSSBOW));
        assertFalse(AgentShopAmmoPolicy.needsFixedAmmoWeapon(WeaponType.CLAW));
        assertFalse(AgentShopAmmoPolicy.needsFixedAmmoWeapon(null));

        assertTrue(AgentShopAmmoPolicy.isRechargeWeaponType(WeaponType.CLAW));
        assertTrue(AgentShopAmmoPolicy.isRechargeWeaponType(WeaponType.GUN));
        assertFalse(AgentShopAmmoPolicy.isRechargeWeaponType(WeaponType.BOW));
        assertFalse(AgentShopAmmoPolicy.isRechargeWeaponType(null));
    }

    @Test
    void shouldCalculateLegacyAmmoThresholds() {
        assertEquals(80, AgentShopAmmoPolicy.triggerThreshold(10, 8));
        assertEquals(100, AgentShopAmmoPolicy.targetThreshold(10, 10));
    }

    @Test
    void shouldDecideFixedAmmoPurchaseFromWeaponAndCount() {
        assertTrue(AgentShopAmmoPolicy.shouldBuyFixedAmmo(WeaponType.BOW, 99, 100));
        assertFalse(AgentShopAmmoPolicy.shouldBuyFixedAmmo(WeaponType.BOW, 100, 100));
        assertFalse(AgentShopAmmoPolicy.shouldBuyFixedAmmo(WeaponType.CLAW, 0, 100));
    }

    @Test
    void shouldMatchRechargeAmmoToWeaponType() {
        assertTrue(AgentShopAmmoPolicy.matchesRechargeWeapon(2070000, WeaponType.CLAW));
        assertTrue(AgentShopAmmoPolicy.matchesRechargeWeapon(2330000, WeaponType.GUN));
        assertFalse(AgentShopAmmoPolicy.matchesRechargeWeapon(2070000, WeaponType.GUN));
        assertFalse(AgentShopAmmoPolicy.matchesRechargeWeapon(2060000, WeaponType.CLAW));
    }

    @Test
    void shouldChooseBestRechargeAmmoByProjectileAttack() {
        List<Item> items = List.of(item(2070000, 5000), item(2070018, 800), item(2060000, 1000));
        IntUnaryOperator watk = itemId -> itemId == 2070018 ? 50 : 10;

        assertEquals(2070018, AgentShopAmmoPolicy.bestRechargeAmmoId(items, WeaponType.CLAW, watk));
        assertEquals(-1, AgentShopAmmoPolicy.bestRechargeAmmoId(items, WeaponType.GUN, watk));
    }

    @Test
    void shouldNeedRechargeOnlyWhenBestAmmoIsRefillableAndBelowThreshold() {
        IntUnaryOperator watk = itemId -> itemId == 2070018 ? 50 : 10;
        IntUnaryOperator slotMax = itemId -> 10000;

        assertTrue(AgentShopAmmoPolicy.needsRecharge(
                List.of(item(2070000, 5000), item(2070018, 800)),
                WeaponType.CLAW, 1000, watk, slotMax));
        assertFalse(AgentShopAmmoPolicy.needsRecharge(
                List.of(item(2070000, 50), item(2070018, 5000)),
                WeaponType.CLAW, 1000, watk, slotMax));
        assertFalse(AgentShopAmmoPolicy.needsRecharge(
                List.of(item(2070018, 10000)),
                WeaponType.CLAW, 1000, watk, slotMax));
        assertFalse(AgentShopAmmoPolicy.needsRecharge(
                List.of(item(2070018, 800)),
                WeaponType.BOW, 1000, watk, slotMax));
    }

    private static Item item(int itemId, int quantity) {
        Item item = mock(Item.class);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getQuantity()).thenReturn((short) quantity);
        return item;
    }
}
