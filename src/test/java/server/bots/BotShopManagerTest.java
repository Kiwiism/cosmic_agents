package server.bots;

import client.BuffStat;
import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.Shop;
import server.ShopFactory;
import server.life.NPC;
import server.maps.MapleMap;
import testutil.Items;

import java.awt.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BotShopManagerTest {
    @Test
    void shouldNotTriggerClawShopVisitWhenTotalStarsAreAboveThreshold() {
        Character bot = clawBotWithStars(800, 1000, 1000, 1000, 1000, 1000);
        BotEntry entry = new BotEntry(bot, null, null);
        MapleMap map = bot.getMap();
        NPC npc = shopNpc(new Point(20, 0));
        Shop shop = mock(Shop.class);

        when(map.getMapObjectsInRange(any(Point.class), anyDouble(), any())).thenReturn(List.of(npc));
        when(shop.getItems()).thenReturn(List.of());

        try (MockedStatic<BotAttackExecutionProvider> attacks =
                     mockStatic(BotAttackExecutionProvider.class, org.mockito.Mockito.CALLS_REAL_METHODS);
             MockedStatic<BotPotionManager> potions = mockStatic(BotPotionManager.class);
             MockedStatic<ShopFactory> shops = mockStatic(ShopFactory.class)) {
            ShopFactory factory = mock(ShopFactory.class);
            shops.when(ShopFactory::getInstance).thenReturn(factory);
            when(factory.getShopForNPC(npc.getId())).thenReturn(shop);
            attacks.when(() -> BotAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.CLAW);
            potions.when(() -> BotPotionManager.countPotions(bot)).thenReturn(new int[]{9999, 9999});

            BotShopManager.onMapChange(entry, bot);
        }

        assertFalse(entry.shopVisitPending);
    }

    @Test
    void shouldTriggerClawShopVisitWhenTotalStarsAreBelowThreshold() {
        Character bot = clawBotWithStars(800, 1000, 1000);
        BotEntry entry = new BotEntry(bot, null, null);
        MapleMap map = bot.getMap();
        NPC npc = shopNpc(new Point(20, 0));
        Shop shop = mock(Shop.class);

        when(map.getMapObjectsInRange(any(Point.class), anyDouble(), any())).thenReturn(List.of(npc));
        when(shop.getItems()).thenReturn(List.of());

        try (MockedStatic<BotAttackExecutionProvider> attacks =
                     mockStatic(BotAttackExecutionProvider.class, org.mockito.Mockito.CALLS_REAL_METHODS);
             MockedStatic<BotPotionManager> potions = mockStatic(BotPotionManager.class);
             MockedStatic<ShopFactory> shops = mockStatic(ShopFactory.class)) {
            ShopFactory factory = mock(ShopFactory.class);
            shops.when(ShopFactory::getInstance).thenReturn(factory);
            when(factory.getShopForNPC(npc.getId())).thenReturn(shop);
            attacks.when(() -> BotAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.CLAW);
            potions.when(() -> BotPotionManager.countPotions(bot)).thenReturn(new int[]{9999, 9999});

            BotShopManager.onMapChange(entry, bot);
        }

        assertTrue(entry.shopVisitPending);
    }

    @Test
    void shouldRechargeStarsWhileShoppingEvenWhenTotalStarsAreAboveTrigger() {
        Character bot = clawBotWithStars(800, 1000, 1000, 1000, 1000, 1000);

        assertFalse(entryWouldTriggerShopVisit(bot, WeaponType.CLAW));
        assertTrue(BotShopManager.shouldRechargeWhileShopping(bot, WeaponType.CLAW));
    }

    @Test
    void shouldBuyArrowsWhileShoppingWhenBelowTargetButAboveTrigger() {
        Character bot = bowBotWithArrows(4500);

        assertFalse(entryWouldTriggerShopVisit(bot, WeaponType.BOW));
        assertTrue(BotShopManager.shouldBuyFixedAmmoWhileShopping(bot, WeaponType.BOW));
    }

    private static Character clawBotWithStars(int... quantities) {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        Inventory use = new Inventory(bot, InventoryType.USE, (byte) 24);
        for (int quantity : quantities) {
            use.addItem(Items.itemWithQuantity(2070000, quantity));
        }
        when(bot.getMap()).thenReturn(map);
        when(bot.getPosition()).thenReturn(new Point(0, 0));
        when(bot.getInventory(InventoryType.USE)).thenReturn(use);
        when(bot.getBuffedValue(any(BuffStat.class))).thenReturn(null);
        return bot;
    }

    private static Character bowBotWithArrows(int quantity) {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        Inventory use = new Inventory(bot, InventoryType.USE, (byte) 24);
        use.addItem(Items.itemWithQuantity(2060000, quantity));
        when(bot.getMap()).thenReturn(map);
        when(bot.getPosition()).thenReturn(new Point(0, 0));
        when(bot.getInventory(InventoryType.USE)).thenReturn(use);
        when(bot.getBuffedValue(any(BuffStat.class))).thenReturn(null);
        return bot;
    }

    private static boolean entryWouldTriggerShopVisit(Character bot, WeaponType weaponType) {
        BotEntry entry = new BotEntry(bot, null, null);
        MapleMap map = bot.getMap();
        NPC npc = shopNpc(new Point(20, 0));
        Shop shop = mock(Shop.class);

        when(map.getMapObjectsInRange(any(Point.class), anyDouble(), any())).thenReturn(List.of(npc));
        when(shop.getItems()).thenReturn(List.<server.ShopItem>of());

        try (MockedStatic<BotAttackExecutionProvider> attacks =
                     mockStatic(BotAttackExecutionProvider.class, org.mockito.Mockito.CALLS_REAL_METHODS);
             MockedStatic<BotPotionManager> potions = mockStatic(BotPotionManager.class);
             MockedStatic<ShopFactory> shops = mockStatic(ShopFactory.class)) {
            ShopFactory factory = mock(ShopFactory.class);
            shops.when(ShopFactory::getInstance).thenReturn(factory);
            when(factory.getShopForNPC(npc.getId())).thenReturn(shop);
            attacks.when(() -> BotAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(weaponType);
            potions.when(() -> BotPotionManager.countPotions(bot)).thenReturn(new int[]{9999, 9999});

            BotShopManager.onMapChange(entry, bot);
        }

        return entry.shopVisitPending;
    }

    private static NPC shopNpc(Point position) {
        NPC npc = mock(NPC.class);
        when(npc.hasShop()).thenReturn(true);
        when(npc.getId()).thenReturn(9010000);
        when(npc.getPosition()).thenReturn(new Point(position));
        return npc;
    }
}
