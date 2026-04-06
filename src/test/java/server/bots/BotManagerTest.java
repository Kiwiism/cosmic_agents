package server.bots;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.StatEffect;
import server.maps.Foothold;
import server.maps.FootholdTree;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotManagerTest {
    @Test
    void shouldParseTransferBotCommands() {
        BotManager.BotTransferCommand command = BotManager.matchBotTransferCommand("transfer Jason to Bob");

        assertNotNull(command);
        assertEquals("Jason", command.botName());
        assertEquals("Bob", command.targetName());
    }

    @Test
    void shouldStillAllowTransferWithoutTo() {
        BotManager.BotTransferCommand command = BotManager.matchBotTransferCommand("transfer Jason Bob");

        assertNotNull(command);
        assertEquals("Jason", command.botName());
        assertEquals("Bob", command.targetName());
    }

    @Test
    void shouldNotTreatGivePhrasesAsBotTransfers() {
        assertNull(BotManager.matchBotTransferCommand("give Jason Bob"));
        assertNull(BotManager.matchBotTransferCommand("give me flaming feather"));
        assertNull(BotManager.matchBotTransferCommand("give flaming feather"));
    }

    @Test
    void shouldCountHpMpAndDualRecoveryItemsAsPotions() {
        Item hpItem = mock(Item.class);
        Item mpItem = mock(Item.class);
        Item dualItem = mock(Item.class);
        Item nonPotion = mock(Item.class);

        when(hpItem.getItemId()).thenReturn(2000002);
        when(hpItem.getQuantity()).thenReturn((short) 10);
        when(mpItem.getItemId()).thenReturn(2000003);
        when(mpItem.getQuantity()).thenReturn((short) 7);
        when(dualItem.getItemId()).thenReturn(2000004);
        when(dualItem.getQuantity()).thenReturn((short) 4);
        when(nonPotion.getItemId()).thenReturn(2040002);
        when(nonPotion.getQuantity()).thenReturn((short) 99);

        StatEffect hpEffect = mock(StatEffect.class);
        StatEffect mpEffect = mock(StatEffect.class);
        StatEffect dualEffect = mock(StatEffect.class);
        StatEffect nonPotionEffect = mock(StatEffect.class);

        when(hpEffect.getHp()).thenReturn((short) 300);
        when(hpEffect.getHpRate()).thenReturn(0d);
        when(hpEffect.getMp()).thenReturn((short) 0);
        when(hpEffect.getMpRate()).thenReturn(0d);

        when(mpEffect.getHp()).thenReturn((short) 0);
        when(mpEffect.getHpRate()).thenReturn(0d);
        when(mpEffect.getMp()).thenReturn((short) 100);
        when(mpEffect.getMpRate()).thenReturn(0d);

        when(dualEffect.getHp()).thenReturn((short) 0);
        when(dualEffect.getHpRate()).thenReturn(50d);
        when(dualEffect.getMp()).thenReturn((short) 0);
        when(dualEffect.getMpRate()).thenReturn(50d);

        when(nonPotionEffect.getHp()).thenReturn((short) 0);
        when(nonPotionEffect.getHpRate()).thenReturn(0d);
        when(nonPotionEffect.getMp()).thenReturn((short) 0);
        when(nonPotionEffect.getMpRate()).thenReturn(0d);

        java.util.Map<Integer, StatEffect> effects = java.util.Map.of(
                2000002, hpEffect,
                2000003, mpEffect,
                2000004, dualEffect,
                2040002, nonPotionEffect);

        int[] counts = BotManager.countPotions(
                java.util.List.of(hpItem, mpItem, dualItem, nonPotion),
                effects::get);

        assertEquals(14, counts[0]);
        assertEquals(11, counts[1]);
    }

    @Test
    void shouldUseCombatRetreatTargetOnlyWithinSameGroundRegion() {
        MapleMap map = createEmptyTestMap(910000020);
        FootholdTree footholds = map.getFootholds();
        footholds.insert(new Foothold(new Point(0, 100), new Point(200, 100), 1));
        BotNavigationGraphProvider.rebuildGraph(map);

        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(map);
        BotEntry entry = new BotEntry(bot, null, null);

        assertTrue(BotManager.shouldUseLocalCombatRetreatTarget(
                entry,
                new Point(100, 100),
                new Point(130, 100),
                new Point(60, 100)));
    }

    @Test
    void shouldRejectCombatRetreatTargetWhenMonsterIsInDifferentRegion() {
        MapleMap map = createEmptyTestMap(910000021);
        FootholdTree footholds = map.getFootholds();
        footholds.insert(new Foothold(new Point(0, 100), new Point(200, 100), 1));
        footholds.insert(new Foothold(new Point(0, 40), new Point(200, 40), 2));
        BotNavigationGraphProvider.rebuildGraph(map);

        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(map);
        BotEntry entry = new BotEntry(bot, null, null);

        assertFalse(BotManager.shouldUseLocalCombatRetreatTarget(
                entry,
                new Point(100, 100),
                new Point(100, 40),
                new Point(60, 100)));
    }

    @Test
    void shouldRejectCombatRetreatTargetWhileClimbing() {
        MapleMap map = createEmptyTestMap(910000022);
        FootholdTree footholds = map.getFootholds();
        footholds.insert(new Foothold(new Point(0, 100), new Point(200, 100), 1));
        footholds.insert(new Foothold(new Point(0, 40), new Point(200, 40), 2));
        map.addRope(new Rope(100, 40, 100, false));
        BotNavigationGraphProvider.rebuildGraph(map);

        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(map);
        BotEntry entry = new BotEntry(bot, null, null);
        entry.climbing = true;
        entry.climbRope = new Rope(100, 40, 100, false);

        assertFalse(BotManager.shouldUseLocalCombatRetreatTarget(
                entry,
                new Point(100, 100),
                new Point(140, 40),
                new Point(60, 100)));
    }

    private static MapleMap createEmptyTestMap(int mapId) {
        MapleMap map = new MapleMap(mapId, 0, 0, mapId, 1.0f);
        map.setFootholds(new FootholdTree(new Point(-2000, -2000), new Point(2000, 2000)));
        return map;
    }
}
