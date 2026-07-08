package server.agents.capabilities.shop;

import server.agents.capabilities.supplies.AgentPotionService;

import server.agents.capabilities.combat.AgentAttackExecutionProvider;

import client.BuffStat;
import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.inventory.AgentInventorySellTrashService;
import server.agents.integration.AgentBotShopRuntime;
import server.agents.integration.AgentShopStateRuntime;
import server.Shop;
import server.ShopFactory;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.NPC;
import server.maps.MapleMap;
import testutil.Items;

import java.awt.*;
import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentShopServiceTest {
    @Test
    void sellTrashNoItemsReplyUsesAgentReplyAdapter() {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(bot.getMap()).thenReturn(map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        try (MockedStatic<AgentInventorySellTrashService> inventories = mockStatic(AgentInventorySellTrashService.class);
             MockedStatic<AgentBotShopRuntime> replies = mockStatic(AgentBotShopRuntime.class)) {
            inventories.when(() -> AgentInventorySellTrashService.collectSellTrashEquips(entry, bot))
                    .thenReturn(List.of());

            AgentShopService.requestSellTrashVisit(entry, bot);

            replies.verify(() -> AgentBotShopRuntime.replyNow(entry, "no trash equips worth selling"));
        }
    }

    @Test
    void shouldNotTriggerClawShopVisitWhenBestStarIsAboveThreshold() {
        Character bot = clawBotWithStars(800, 1000, 1000, 1000, 1000, 1000); // 5800 of the best star
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        MapleMap map = bot.getMap();
        NPC npc = shopNpc(new Point(20, 0));
        Shop shop = mock(Shop.class);

        when(map.getMapObjectsInRange(any(Point.class), anyDouble(), any())).thenReturn(List.of(npc));
        when(shop.getItems()).thenReturn(List.of());

        try (Seam seam = withStarStats();
             MockedStatic<AgentAttackExecutionProvider> attacks =
                     mockStatic(AgentAttackExecutionProvider.class, org.mockito.Mockito.CALLS_REAL_METHODS);
             MockedStatic<AgentPotionService> potions = mockStatic(AgentPotionService.class);
             MockedStatic<ShopFactory> shops = mockStatic(ShopFactory.class)) {
            ShopFactory factory = mock(ShopFactory.class);
            shops.when(ShopFactory::getInstance).thenReturn(factory);
            when(factory.getShopForNPC(npc.getId())).thenReturn(shop);
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.CLAW);
            potions.when(() -> AgentPotionService.countPotions(bot)).thenReturn(new int[]{9999, 9999});

            AgentShopService.onMapChange(entry, bot);
        }

        assertFalse(AgentShopStateRuntime.shopVisitPending(entry));
    }

    @Test
    void shouldTriggerClawShopVisitWhenBestStarIsBelowThreshold() {
        Character bot = clawBotWithStars(800, 1000, 1000); // 2800 of the best star
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        MapleMap map = bot.getMap();
        NPC npc = shopNpc(new Point(20, 0));
        Shop shop = mock(Shop.class);

        when(map.getMapObjectsInRange(any(Point.class), anyDouble(), any())).thenReturn(List.of(npc));
        when(shop.getItems()).thenReturn(List.of());

        try (Seam seam = withStarStats();
             MockedStatic<AgentAttackExecutionProvider> attacks =
                     mockStatic(AgentAttackExecutionProvider.class, org.mockito.Mockito.CALLS_REAL_METHODS);
             MockedStatic<AgentPotionService> potions = mockStatic(AgentPotionService.class);
             MockedStatic<ShopFactory> shops = mockStatic(ShopFactory.class)) {
            ShopFactory factory = mock(ShopFactory.class);
            shops.when(ShopFactory::getInstance).thenReturn(factory);
            when(factory.getShopForNPC(npc.getId())).thenReturn(shop);
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.CLAW);
            potions.when(() -> AgentPotionService.countPotions(bot)).thenReturn(new int[]{9999, 9999});

            AgentShopService.onMapChange(entry, bot);
        }

        assertTrue(AgentShopStateRuntime.shopVisitPending(entry));
    }

    @Test
    void shouldTriggerRechargeForDepletedBestStarMaskedByWeakerStars() {
        // Plenty of weak stars (id 2070000) plus a low stack of the BEST star (id 2070018).
        // Total (5800) is above the trigger, but the best star (800) is below it.
        Character bot = clawBotWithStarItems(new int[]{2070000, 2070000, 2070000, 2070000, 2070000},
                new int[]{1000, 1000, 1000, 1000, 1000});
        bot.getInventory(InventoryType.USE).addItem(Items.itemWithQuantity(2070018, 800));

        try (Seam seam = withStarStats()) {
            assertTrue(entryWouldTriggerShopVisit(bot, WeaponType.CLAW));
            assertTrue(AgentShopService.shouldRechargeWhileShopping(bot, WeaponType.CLAW));
        }
    }

    @Test
    void shouldNotRechargeWhenBestStarIsHealthyDespiteLowWeakerStars() {
        // Best star (2070018) is full and above threshold; a near-empty weak star must not trigger.
        Character bot = clawBotWithStarItems(new int[]{2070000, 2070018}, new int[]{50, 5000});

        try (Seam seam = withStarStats()) {
            assertFalse(entryWouldTriggerShopVisit(bot, WeaponType.CLAW));
            assertFalse(AgentShopService.shouldRechargeWhileShopping(bot, WeaponType.CLAW));
        }
    }

    @Test
    void shouldBuyArrowsWhileShoppingWhenBelowTargetButAboveTrigger() {
        Character bot = bowBotWithArrows(4500);

        assertFalse(entryWouldTriggerShopVisit(bot, WeaponType.BOW));
        assertTrue(AgentShopService.shouldBuyFixedAmmoWhileShopping(bot, WeaponType.BOW));
    }

    @Test
    void shouldTriggerSellTrashShopVisitEvenWhenNoResupplyIsNeeded() {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        NPC npc = shopNpc(new Point(20, 0));
        Shop shop = mock(Shop.class);

        when(bot.getMap()).thenReturn(map);
        when(bot.getPosition()).thenReturn(new Point(0, 0));
        when(bot.getInventory(InventoryType.USE)).thenReturn(new Inventory(bot, InventoryType.USE, (byte) 24));
        when(bot.getBuffedValue(any(BuffStat.class))).thenReturn(null);
        when(map.getMapObjectsInRange(any(Point.class), anyDouble(), any())).thenReturn(List.of(npc));
        when(shop.getItems()).thenReturn(List.of());

        try (MockedStatic<AgentAttackExecutionProvider> attacks =
                     mockStatic(AgentAttackExecutionProvider.class, org.mockito.Mockito.CALLS_REAL_METHODS);
             MockedStatic<AgentPotionService> potions = mockStatic(AgentPotionService.class);
             MockedStatic<ShopFactory> shops = mockStatic(ShopFactory.class);
             MockedStatic<AgentInventorySellTrashService> inventories = mockStatic(AgentInventorySellTrashService.class)) {
            ShopFactory factory = mock(ShopFactory.class);
            shops.when(ShopFactory::getInstance).thenReturn(factory);
            when(factory.getShopForNPC(npc.getId())).thenReturn(shop);
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.CLAW);
            potions.when(() -> AgentPotionService.countPotions(bot)).thenReturn(new int[]{9999, 9999});
            inventories.when(() -> AgentInventorySellTrashService.collectSellTrashEquips(entry, bot))
                    .thenReturn(List.of(mock(Item.class)));

            AgentShopService.requestSellTrashVisit(entry, bot);
        }

        assertTrue(AgentShopStateRuntime.shopVisitPending(entry));
        assertTrue(AgentShopStateRuntime.shopSellTrashPending(entry));
        assertEquals(new Point(20, 0), AgentShopStateRuntime.shopNpcPosition(entry));
    }

    private static Character clawBotWithStars(int... quantities) {
        int[] ids = new int[quantities.length];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = 2070000;
        }
        return clawBotWithStarItems(ids, quantities);
    }

    private static Character clawBotWithStarItems(int[] ids, int[] quantities) {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        Inventory use = new Inventory(bot, InventoryType.USE, (byte) 24);
        for (int i = 0; i < ids.length; i++) {
            use.addItem(Items.itemWithQuantity(ids[i], quantities[i]));
        }
        when(bot.getMap()).thenReturn(map);
        when(bot.getPosition()).thenReturn(new Point(0, 0));
        when(bot.getInventory(InventoryType.USE)).thenReturn(use);
        when(bot.getBuffedValue(any(BuffStat.class))).thenReturn(null);
        return bot;
    }

    // Stub the ItemInformationProvider-backed seam: star 2070018 is the strongest, all stacks
    // are well under slot-max so any partial stack counts as refillable. Restored on close.
    private static Seam withStarStats() {
        IntUnaryOperator prevWatk = AgentShopService.projectileWatk;
        AgentShopService.SlotMaxLookup prevSlot = AgentShopService.ammoSlotMax;
        AgentShopService.projectileWatk = id -> id == 2070018 ? 50 : 10;
        AgentShopService.ammoSlotMax = (bot, id) -> (short) 10000;
        return new Seam(prevWatk, prevSlot);
    }

    private record Seam(IntUnaryOperator watk, AgentShopService.SlotMaxLookup slot) implements AutoCloseable {
        @Override
        public void close() {
            AgentShopService.projectileWatk = watk;
            AgentShopService.ammoSlotMax = slot;
        }
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
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        MapleMap map = bot.getMap();
        NPC npc = shopNpc(new Point(20, 0));
        Shop shop = mock(Shop.class);

        when(map.getMapObjectsInRange(any(Point.class), anyDouble(), any())).thenReturn(List.of(npc));
        when(shop.getItems()).thenReturn(List.<server.ShopItem>of());

        try (MockedStatic<AgentAttackExecutionProvider> attacks =
                     mockStatic(AgentAttackExecutionProvider.class, org.mockito.Mockito.CALLS_REAL_METHODS);
             MockedStatic<AgentPotionService> potions = mockStatic(AgentPotionService.class);
             MockedStatic<ShopFactory> shops = mockStatic(ShopFactory.class)) {
            ShopFactory factory = mock(ShopFactory.class);
            shops.when(ShopFactory::getInstance).thenReturn(factory);
            when(factory.getShopForNPC(npc.getId())).thenReturn(shop);
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(weaponType);
            potions.when(() -> AgentPotionService.countPotions(bot)).thenReturn(new int[]{9999, 9999});

            AgentShopService.onMapChange(entry, bot);
        }

        return AgentShopStateRuntime.shopVisitPending(entry);
    }

    private static NPC shopNpc(Point position) {
        NPC npc = mock(NPC.class);
        when(npc.hasShop()).thenReturn(true);
        when(npc.getId()).thenReturn(9010000);
        when(npc.getPosition()).thenReturn(new Point(position));
        return npc;
    }
}
