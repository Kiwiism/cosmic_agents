package server.bots;

import server.agents.capabilities.navigation.AgentNavigationGraphService;

import server.agents.capabilities.navigation.AgentNavigationGraph;

import server.agents.capabilities.looting.AgentLootEligibility;

import server.agents.capabilities.movement.AgentMovementProfile;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy;
import server.agents.integration.AgentBotInventoryRuntime;
import server.agents.integration.AgentBotManualTradeStateRuntime;
import server.Trade;
import server.maps.Foothold;
import server.maps.MapItem;
import server.maps.MapleMap;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotInventoryManagerTest {
    @Test
    void shouldOnlyAnnounceTradeInviteOnFirstBatchOfSequence() throws Exception {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
        Character bot = entry.bot;
        Character owner = entry.owner;
        Method startTradeSequence = method(BotInventoryManager.class,
                "startTradeSequence",
                String.class, Character.class, List.class, int.class, boolean.class, BotEntry.class, Character.class);
        Method openTradeBatch = method(BotInventoryManager.class,
                "openTradeBatch",
                BotEntry.class, Character.class, List.class, int.class);

        when(owner.getId()).thenReturn(42);
        when(owner.getTrade()).thenReturn(null);
        when(bot.getTrade()).thenReturn(null);

        try (MockedStatic<AgentBotInventoryRuntime> replies = mockStatic(AgentBotInventoryRuntime.class);
             MockedStatic<Trade> trades = mockStatic(Trade.class)) {
            startTradeSequence.invoke(null, "trash", owner, List.of(mock(Item.class)), 0, false, entry, bot);
            openTradeBatch.invoke(null, entry, bot, List.of(mock(Item.class)), 0);

            replies.verify(() -> AgentBotInventoryRuntime.replyNow(eq(entry), anyString()), times(1));
        }
    }

    @Test
    void shouldExposeExpandedTradeMessagePools() {
        List<String> invitationMsgs = AgentDialogueCatalog.tradeInvitationReplies();
        List<String> freebieMsgs = AgentDialogueCatalog.tradeFreebieReplies();

        assertTrue(invitationMsgs.size() >= 20);
        assertTrue(invitationMsgs.contains("ready when u are"));
        assertTrue(invitationMsgs.contains("trade time"));

        assertTrue(freebieMsgs.size() >= 16);
        assertTrue(freebieMsgs.contains("enjoy"));
        assertTrue(freebieMsgs.contains(":)"));
        assertTrue(freebieMsgs.contains("hope that helps"));
    }

    @Test
    void shouldCancelUnmanagedBotTradeWhenManualTimeoutExpires() {
        BotEntry entry = new BotEntry(mock(Character.class), null, null);
        Character bot = entry.bot;
        Trade trade = mock(Trade.class);

        when(bot.getId()).thenReturn(99);
        when(bot.getTrade()).thenReturn(trade);

        BotInventoryManager.tickManualTrade(entry, bot);
        AgentBotManualTradeStateRuntime.setTimeoutMs(entry, BotMovementManager.cfg.TICK_MS);

        try (MockedStatic<Trade> trades = mockStatic(Trade.class)) {
            BotInventoryManager.tickManualTrade(entry, bot);

            trades.verify(() -> Trade.cancelTrade(bot, Trade.TradeResult.NO_RESPONSE));
            assertNull(AgentBotManualTradeStateRuntime.tradeRef(entry));
            assertEquals(0, AgentBotManualTradeStateRuntime.timeoutMs(entry));
        }
    }

    @Test
    void shouldFilterProtectedEquipsOutOfSellTrashOnly() {
        Equip sellable = mock(Equip.class);
        Equip highIntAllJob = mock(Equip.class);
        Equip highDexWarrior = mock(Equip.class);
        Equip nonWeaponWatk = mock(Equip.class);
        Equip scrolled = mock(Equip.class);
        Equip pirateDex = mock(Equip.class);
        Equip currentWarriorWeapon = mock(Equip.class);
        Equip baseWarriorWeapon = mock(Equip.class);
        Equip currentMageWeapon = mock(Equip.class);
        Equip baseMageWeapon = mock(Equip.class);

        when(sellable.getItemId()).thenReturn(1000001);
        when(highIntAllJob.getItemId()).thenReturn(1082001);
        when(highDexWarrior.getItemId()).thenReturn(1000002);
        when(nonWeaponWatk.getItemId()).thenReturn(1072001);
        when(scrolled.getItemId()).thenReturn(1040001);
        when(pirateDex.getItemId()).thenReturn(1050001);
        when(currentWarriorWeapon.getItemId()).thenReturn(1302000);
        when(currentMageWeapon.getItemId()).thenReturn(1372000);

        when(sellable.getStr()).thenReturn((short) 3);
        when(highIntAllJob.getInt()).thenReturn((short) 6);
        when(highDexWarrior.getDex()).thenReturn((short) 6);
        when(nonWeaponWatk.getWatk()).thenReturn((short) 1);
        when(scrolled.getLevel()).thenReturn((byte) 1);
        when(pirateDex.getDex()).thenReturn((short) 6);
        when(currentWarriorWeapon.getWatk()).thenReturn((short) 24);
        when(baseWarriorWeapon.getWatk()).thenReturn((short) 20);
        when(currentMageWeapon.getMatk()).thenReturn((short) 29);
        when(baseMageWeapon.getMatk()).thenReturn((short) 25);

        assertTrue(!BotInventoryManager.hasProtectedSellTrashStat(Map.of("reqJob", 1), sellable, 6, 10));
        assertTrue(BotInventoryManager.hasProtectedSellTrashStat(Map.of("reqJob", 0), highIntAllJob, 6, 10));
        assertTrue(BotInventoryManager.hasProtectedSellTrashStat(Map.of("reqJob", 1), highDexWarrior, 6, 10));
        assertTrue(BotInventoryManager.shouldKeepForSellTrash(null, nonWeaponWatk));
        assertTrue(BotInventoryManager.shouldKeepForSellTrash(null, scrolled));
        assertTrue(BotInventoryManager.hasProtectedSellTrashStat(Map.of("reqJob", 16), pirateDex, 6, 10));

        // Stat at or below the item's WZ base (not scrolled above base) and below the pure
        // threshold => trash, even though it clears the old flat-6 bar.
        assertTrue(!BotInventoryManager.hasProtectedSellTrashStat(Map.of("reqJob", 1, "DEX", 6), highDexWarrior, 6, 10));
        // A high absolute stat (>= pure threshold) still protects, even sitting at base.
        Equip pureHighDex = mock(Equip.class);
        when(pureHighDex.getDex()).thenReturn((short) 10);
        assertTrue(BotInventoryManager.hasProtectedSellTrashStat(Map.of("reqJob", 1, "DEX", 10), pureHighDex, 6, 10));
        assertTrue(BotInventoryManager.hasProtectedSellTrashWeaponStat(Map.of("reqJob", 1), currentWarriorWeapon, baseWarriorWeapon));
        assertTrue(BotInventoryManager.hasProtectedSellTrashWeaponStat(Map.of("reqJob", 2), currentMageWeapon, baseMageWeapon));
    }

    @Test
    void shouldClampReservedTradePages() {
        assertEquals(1, BotInventoryManager.clampTradePage(-4, 0));
        assertEquals(1, BotInventoryManager.clampTradePage(0, 5));
        assertEquals(1, BotInventoryManager.clampTradePage(1, 9));
        assertEquals(2, BotInventoryManager.clampTradePage(2, 10));
        assertEquals(2, BotInventoryManager.clampTradePage(99, 10));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSortOwnReservedEquipsByEquipmentScore() throws Exception {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(Job.FIGHTER);

        Equip highScore = mock(Equip.class);
        when(highScore.getWatk()).thenReturn((short) 10);
        when(highScore.getStr()).thenReturn((short) 5);
        when(highScore.getDex()).thenReturn((short) 2);
        when(highScore.getMatk()).thenReturn((short) 0);
        when(highScore.getItemId()).thenReturn(1302000);
        when(highScore.getPosition()).thenReturn((short) 3);

        Equip lowScore = mock(Equip.class);
        when(lowScore.getWatk()).thenReturn((short) 1);
        when(lowScore.getStr()).thenReturn((short) 1);
        when(lowScore.getDex()).thenReturn((short) 0);
        when(lowScore.getMatk()).thenReturn((short) 0);
        when(lowScore.getItemId()).thenReturn(1050000);
        when(lowScore.getPosition()).thenReturn((short) 1);

        Equip midScore = mock(Equip.class);
        when(midScore.getWatk()).thenReturn((short) 3);
        when(midScore.getStr()).thenReturn((short) 2);
        when(midScore.getDex()).thenReturn((short) 1);
        when(midScore.getMatk()).thenReturn((short) 0);
        when(midScore.getItemId()).thenReturn(1070000);
        when(midScore.getPosition()).thenReturn((short) 2);

        List<Item> sorted = AgentInventoryTradePolicy.sortReservedEquipsByTradeScore(
                new java.util.ArrayList<>(List.of(highScore, lowScore, midScore)),
                bot);

        assertEquals(List.of(lowScore, midScore, highScore), sorted);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSortJunkAndOtherReserveByItemId() throws Exception {
        Equip highScoreLowId = mock(Equip.class);
        when(highScoreLowId.getWatk()).thenReturn((short) 10);
        when(highScoreLowId.getStr()).thenReturn((short) 5);
        when(highScoreLowId.getDex()).thenReturn((short) 2);
        when(highScoreLowId.getMatk()).thenReturn((short) 0);
        when(highScoreLowId.getItemId()).thenReturn(1000000);
        when(highScoreLowId.getPosition()).thenReturn((short) 3);

        Equip lowScoreHighId = mock(Equip.class);
        when(lowScoreHighId.getWatk()).thenReturn((short) 1);
        when(lowScoreHighId.getStr()).thenReturn((short) 1);
        when(lowScoreHighId.getDex()).thenReturn((short) 0);
        when(lowScoreHighId.getMatk()).thenReturn((short) 0);
        when(lowScoreHighId.getItemId()).thenReturn(1302000);
        when(lowScoreHighId.getPosition()).thenReturn((short) 1);

        Equip midId = mock(Equip.class);
        when(midId.getWatk()).thenReturn((short) 3);
        when(midId.getStr()).thenReturn((short) 2);
        when(midId.getDex()).thenReturn((short) 1);
        when(midId.getMatk()).thenReturn((short) 0);
        when(midId.getItemId()).thenReturn(1070000);
        when(midId.getPosition()).thenReturn((short) 2);

        Method sortEquipsByItemId = method(BotInventoryManager.class, "sortEquipsByItemId", List.class);

        List<Item> sorted = (List<Item>) sortEquipsByItemId.invoke(
                null,
                new java.util.ArrayList<>(List.of(lowScoreHighId, highScoreLowId, midId)));

        assertEquals(List.of(highScoreLowId, midId, lowScoreHighId), sorted);
    }

    @Test
    void shouldExcludeOneWayPatrolNeighborFromLootRoamScope() {
        MapleMap map = spy(new MapleMap(910009054, 0, 0, 910009054, 1.0f));
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        Foothold homeFoothold = new Foothold(new Point(0, 100), new Point(100, 100), 1);
        Foothold oneWayFoothold = new Foothold(new Point(200, 140), new Point(300, 140), 2);
        Foothold returnableFoothold = new Foothold(new Point(400, 100), new Point(500, 100), 3);
        footholds.insert(homeFoothold);
        footholds.insert(oneWayFoothold);
        footholds.insert(returnableFoothold);
        map.setFootholds(footholds);

        AgentNavigationGraph.Region homeRegion = new AgentNavigationGraph.Region(
                1, List.of(new AgentNavigationGraph.Segment(homeFoothold)));
        AgentNavigationGraph.Region oneWayRegion = new AgentNavigationGraph.Region(
                2, List.of(new AgentNavigationGraph.Segment(oneWayFoothold)));
        AgentNavigationGraph.Region returnableRegion = new AgentNavigationGraph.Region(
                3, List.of(new AgentNavigationGraph.Segment(returnableFoothold)));
        AgentNavigationGraph graph = new AgentNavigationGraph(
                map.getId(),
                1,
                AgentMovementProfile.base(),
                List.of(homeRegion, oneWayRegion, returnableRegion),
                Map.of(1, homeRegion, 2, oneWayRegion, 3, returnableRegion),
                Map.of(1, 1, 2, 2, 3, 3),
                Map.of(
                        1, List.of(
                                new AgentNavigationGraph.Edge(1, 2, AgentNavigationGraph.EdgeType.DROP,
                                        new Point(100, 100), new Point(200, 140), 0, 0, 0, 0, 0, 100),
                                new AgentNavigationGraph.Edge(1, 3, AgentNavigationGraph.EdgeType.WALK,
                                        new Point(100, 100), new Point(400, 100), 0, 0, 0, 0, 0, 120)),
                        3, List.of(new AgentNavigationGraph.Edge(3, 1, AgentNavigationGraph.EdgeType.WALK,
                                new Point(400, 100), new Point(100, 100), 0, 0, 0, 0, 0, 120))),
                Set.of());

        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(map);
        when(bot.getPosition()).thenReturn(new Point(50, 100));
        when(bot.getInventory(any())).thenReturn((Inventory) null);
        BotEntry entry = new BotEntry(bot, null, null);

        MapItem oneWayLoot = mock(MapItem.class);
        when(oneWayLoot.getPosition()).thenReturn(new Point(240, 140));
        MapItem returnableLoot = mock(MapItem.class);
        when(returnableLoot.getPosition()).thenReturn(new Point(440, 100));
        doReturn(List.of(oneWayLoot, returnableLoot)).when(map).getDroppedItems();

        try (MockedStatic<AgentNavigationGraphService> graphProvider =
                     mockStatic(AgentNavigationGraphService.class, org.mockito.Mockito.CALLS_REAL_METHODS);
             MockedStatic<AgentLootEligibility> lootEligibility =
                     mockStatic(AgentLootEligibility.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            graphProvider.when(() -> AgentNavigationGraphService.peekBestGraph(eq(map), any())).thenReturn(graph);
            lootEligibility.when(() -> AgentLootEligibility.canBotTargetLoot(
                    eq(entry), eq(bot), eq(map), eq(oneWayLoot), anyLong())).thenReturn(true);
            lootEligibility.when(() -> AgentLootEligibility.canBotTargetLoot(
                    eq(entry), eq(bot), eq(map), eq(returnableLoot), anyLong())).thenReturn(true);

            Point target = BotInventoryManager.findNearestPatrolLootTarget(entry, 1);

            assertEquals(new Point(440, 100), target);
        }
    }

    @Test
    void shouldPatrolTowardMobLootWhenBasePickupEligibilityAllowsIt() {
        MapleMap map = spy(new MapleMap(910000053, 0, 0, 910000053, 1.0f));
        Foothold foothold = new Foothold(new Point(0, 100), new Point(500, 100), 1);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-1000, -1000), new Point(1000, 2000));
        footholds.insert(foothold);
        map.setFootholds(footholds);

        AgentNavigationGraph.Region region = new AgentNavigationGraph.Region(
                1, List.of(new AgentNavigationGraph.Segment(foothold)));
        AgentNavigationGraph graph = new AgentNavigationGraph(
                map.getId(),
                1,
                AgentMovementProfile.base(),
                List.of(region),
                Map.of(1, region),
                Map.of(1, 1),
                Map.of(),
                Set.of());

        Character bot = mock(Character.class);
        when(bot.getId()).thenReturn(88);
        when(bot.getMap()).thenReturn(map);
        when(bot.getPosition()).thenReturn(new Point(50, 100));
        when(bot.getInventory(any())).thenReturn((Inventory) null);
        BotEntry entry = new BotEntry(bot, null, null);

        MapItem loot = mock(MapItem.class);
        when(loot.getObjectId()).thenReturn(1);
        when(loot.getPosition()).thenReturn(new Point(240, 100));
        when(loot.isPickedUp()).thenReturn(false);
        when(loot.canBePickedBy(any(Character.class))).thenReturn(true);
        when(loot.getDropTime()).thenReturn(System.currentTimeMillis() - 16_000L);
        when(loot.getOwnerId()).thenReturn(99);
        when(loot.isPlayerDrop()).thenReturn(false);
        when(loot.getItemId()).thenReturn(0);
        when(loot.getMeso()).thenReturn(1);
        doReturn(List.of(loot)).when(map).getDroppedItems();
        doReturn(loot).when(map).getMapObject(1);

        BotManager manager = mock(BotManager.class);

        try (MockedStatic<AgentNavigationGraphService> graphProvider =
                     mockStatic(AgentNavigationGraphService.class, org.mockito.Mockito.CALLS_REAL_METHODS);
             MockedStatic<BotManager> botManagers =
                     mockStatic(BotManager.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            graphProvider.when(() -> AgentNavigationGraphService.peekBestGraph(eq(map), any())).thenReturn(graph);
            botManagers.when(BotManager::getInstance).thenReturn(manager);

            assertEquals(new Point(240, 100), BotInventoryManager.findNearestPatrolLootTarget(entry, 1));
        }
    }

    private static Method method(Class<?> type, String name, Class<?>... parameterTypes) throws Exception {
        Method method = type.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }
}
