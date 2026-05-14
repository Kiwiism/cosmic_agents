package server.bots;

import client.Character;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.Trade;
import server.maps.Foothold;
import server.maps.MapItem;
import server.maps.MapleMap;

import java.awt.*;
import java.lang.reflect.Field;
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
import static org.mockito.Mockito.doAnswer;
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
        BotManager manager = spy(BotManager.getInstance());
        Method startTradeSequence = method(BotInventoryManager.class,
                "startTradeSequence",
                String.class, Character.class, List.class, int.class, boolean.class, BotEntry.class, Character.class);
        Method openTradeBatch = method(BotInventoryManager.class,
                "openTradeBatch",
                BotEntry.class, Character.class, List.class, int.class);

        when(owner.getId()).thenReturn(42);
        when(owner.getTrade()).thenReturn(null);
        when(bot.getTrade()).thenReturn(null);
        doAnswer(invocation -> null).when(manager).botReply(eq(entry), anyString());

        try (MockedStatic<BotManager> botManagers = mockStatic(BotManager.class, org.mockito.Mockito.CALLS_REAL_METHODS);
             MockedStatic<Trade> trades = mockStatic(Trade.class)) {
            botManagers.when(BotManager::getInstance).thenReturn(manager);

            startTradeSequence.invoke(null, "trash", owner, List.of(mock(Item.class)), 0, false, entry, bot);
            openTradeBatch.invoke(null, entry, bot, List.of(mock(Item.class)), 0);

            verify(manager, times(1)).botReply(eq(entry), anyString());
        }
    }

    @Test
    void shouldExposeExpandedTradeMessagePools() throws Exception {
        List<String> invitationMsgs = listField("TRADE_INVITATION_MSGS");
        List<String> freebieMsgs = listField("TRADE_FREEBIE_QUIPS");

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
        entry.manualTradeTimeoutMs = BotMovementManager.cfg.TICK_MS;

        try (MockedStatic<Trade> trades = mockStatic(Trade.class)) {
            BotInventoryManager.tickManualTrade(entry, bot);

            trades.verify(() -> Trade.cancelTrade(bot, Trade.TradeResult.NO_RESPONSE));
            assertNull(entry.manualTradeRef);
            assertTrue(entry.manualTradeTimeoutMs == 0);
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

        assertTrue(!BotInventoryManager.hasProtectedSellTrashStat(Map.of("reqJob", 1), sellable, 6));
        assertTrue(BotInventoryManager.hasProtectedSellTrashStat(Map.of("reqJob", 0), highIntAllJob, 6));
        assertTrue(BotInventoryManager.hasProtectedSellTrashStat(Map.of("reqJob", 1), highDexWarrior, 6));
        assertTrue(BotInventoryManager.shouldKeepForSellTrash(null, nonWeaponWatk));
        assertTrue(BotInventoryManager.shouldKeepForSellTrash(null, scrolled));
        assertTrue(BotInventoryManager.hasProtectedSellTrashStat(Map.of("reqJob", 16), pirateDex, 6));
        assertTrue(BotInventoryManager.hasProtectedSellTrashWeaponStat(Map.of("reqJob", 1), currentWarriorWeapon, baseWarriorWeapon));
        assertTrue(BotInventoryManager.hasProtectedSellTrashWeaponStat(Map.of("reqJob", 2), currentMageWeapon, baseMageWeapon));
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

        BotNavigationGraph.Region homeRegion = new BotNavigationGraph.Region(
                1, List.of(new BotNavigationGraph.Segment(homeFoothold)));
        BotNavigationGraph.Region oneWayRegion = new BotNavigationGraph.Region(
                2, List.of(new BotNavigationGraph.Segment(oneWayFoothold)));
        BotNavigationGraph.Region returnableRegion = new BotNavigationGraph.Region(
                3, List.of(new BotNavigationGraph.Segment(returnableFoothold)));
        BotNavigationGraph graph = new BotNavigationGraph(
                map.getId(),
                1,
                BotMovementProfile.base(),
                List.of(homeRegion, oneWayRegion, returnableRegion),
                Map.of(1, homeRegion, 2, oneWayRegion, 3, returnableRegion),
                Map.of(1, 1, 2, 2, 3, 3),
                Map.of(
                        1, List.of(
                                new BotNavigationGraph.Edge(1, 2, BotNavigationGraph.EdgeType.DROP,
                                        new Point(100, 100), new Point(200, 140), 0, 0, 0, 0, 0, 100),
                                new BotNavigationGraph.Edge(1, 3, BotNavigationGraph.EdgeType.WALK,
                                        new Point(100, 100), new Point(400, 100), 0, 0, 0, 0, 0, 120)),
                        3, List.of(new BotNavigationGraph.Edge(3, 1, BotNavigationGraph.EdgeType.WALK,
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

        try (MockedStatic<BotNavigationGraphProvider> graphProvider =
                     mockStatic(BotNavigationGraphProvider.class, org.mockito.Mockito.CALLS_REAL_METHODS);
             MockedStatic<BotLootEligibility> lootEligibility =
                     mockStatic(BotLootEligibility.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            graphProvider.when(() -> BotNavigationGraphProvider.peekGraph(map)).thenReturn(graph);
            lootEligibility.when(() -> BotLootEligibility.canBotTargetLoot(
                    eq(entry), eq(bot), eq(map), eq(oneWayLoot), anyLong())).thenReturn(true);
            lootEligibility.when(() -> BotLootEligibility.canBotTargetLoot(
                    eq(entry), eq(bot), eq(map), eq(returnableLoot), anyLong())).thenReturn(true);

            Point target = BotInventoryManager.findNearestPatrolLootTarget(entry, 1);

            assertEquals(new Point(440, 100), target);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> listField(String name) throws Exception {
        return (List<String>) field(BotInventoryManager.class, name).get(null);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> type, String name, Class<?>... parameterTypes) throws Exception {
        Method method = type.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }
}
