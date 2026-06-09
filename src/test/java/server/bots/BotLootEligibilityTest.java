package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.maps.MapItem;
import server.maps.MapleMap;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BotLootEligibilityTest {
    @Test
    void shouldDelayTargetingBotInventoryDropsForFifteenSeconds() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mock(Character.class);
        MapItem drop = mockLoot(1, 99, true, System.currentTimeMillis() - 14_000L);
        BotEntry entry = new BotEntry(bot, mock(Character.class), null);
        BotManager manager = mock(BotManager.class);
        Character dropBotOwner = mock(Character.class);

        when(bot.getId()).thenReturn(88);
        when(manager.getActiveOwnerByBotCharId(99)).thenReturn(dropBotOwner);
        doReturn(drop).when(map).getMapObject(1);

        try (MockedStatic<BotManager> botManagers = mockStatic(BotManager.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            botManagers.when(BotManager::getInstance).thenReturn(manager);

            assertFalse(BotLootEligibility.canBotTargetLoot(entry, bot, map, drop, System.currentTimeMillis()));
        }
    }

    @Test
    void shouldAllowTargetingBotInventoryDropsAfterFifteenSeconds() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mock(Character.class);
        MapItem drop = mockLoot(1, 99, true, System.currentTimeMillis() - 16_000L);
        BotEntry entry = new BotEntry(bot, mock(Character.class), null);
        BotManager manager = mock(BotManager.class);
        Character dropBotOwner = mock(Character.class);

        when(bot.getId()).thenReturn(88);
        when(manager.getActiveOwnerByBotCharId(99)).thenReturn(dropBotOwner);
        doReturn(drop).when(map).getMapObject(1);

        try (MockedStatic<BotManager> botManagers = mockStatic(BotManager.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            botManagers.when(BotManager::getInstance).thenReturn(manager);

            assertTrue(BotLootEligibility.canBotTargetLoot(entry, bot, map, drop, System.currentTimeMillis()));
        }
    }

    @Test
    void shouldDelayTargetingOwnBotInventoryDropsForFifteenSeconds() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        MapItem drop = mockLoot(1, 88, true, System.currentTimeMillis() - 14_000L);
        BotEntry entry = new BotEntry(bot, owner, null);
        BotManager manager = mock(BotManager.class);

        when(bot.getId()).thenReturn(88);
        when(manager.getActiveOwnerByBotCharId(88)).thenReturn(owner);
        doReturn(drop).when(map).getMapObject(1);

        try (MockedStatic<BotManager> botManagers = mockStatic(BotManager.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            botManagers.when(BotManager::getInstance).thenReturn(manager);

            assertFalse(BotLootEligibility.canBotTargetLoot(entry, bot, map, drop, System.currentTimeMillis()));
        }
    }

    @Test
    void shouldKeepExistingThreeSecondTargetDelayForHumanInventoryDrops() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mock(Character.class);
        MapItem drop = mockLoot(1, 77, true, System.currentTimeMillis() - 4_000L);
        BotEntry entry = new BotEntry(bot, mock(Character.class), null);
        BotManager manager = mock(BotManager.class);

        when(bot.getId()).thenReturn(88);
        doReturn(drop).when(map).getMapObject(1);

        try (MockedStatic<BotManager> botManagers = mockStatic(BotManager.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            botManagers.when(BotManager::getInstance).thenReturn(manager);

            assertTrue(BotLootEligibility.canBotTargetLoot(entry, bot, map, drop, System.currentTimeMillis()));
        }
    }

    @Test
    void shouldAllowMobLootWhenBasePickupEligibilityAllowsIt() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        MapItem drop = mockLoot(1, 77, false, System.currentTimeMillis() - 16_000L);
        BotEntry entry = new BotEntry(bot, owner, null);

        when(bot.getId()).thenReturn(88);
        when(owner.getId()).thenReturn(10);
        doReturn(drop).when(map).getMapObject(1);

        assertTrue(BotLootEligibility.canBotTargetLoot(entry, bot, map, drop, System.currentTimeMillis()));
    }

    @Test
    void shouldRejectMobLootWhenBasePickupEligibilityRejectsIt() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        MapItem drop = mockLoot(1, 77, false, System.currentTimeMillis() - 16_000L);
        BotEntry entry = new BotEntry(bot, owner, null);

        when(bot.getId()).thenReturn(88);
        when(owner.getId()).thenReturn(10);
        when(drop.canBePickedBy(bot)).thenReturn(false);
        doReturn(drop).when(map).getMapObject(1);

        assertFalse(BotLootEligibility.canBotTargetLoot(entry, bot, map, drop, System.currentTimeMillis()));
    }

    private static MapItem mockLoot(int objectId, int ownerId, boolean playerDrop, long dropTime) {
        MapItem drop = mock(MapItem.class);
        when(drop.getObjectId()).thenReturn(objectId);
        when(drop.getPosition()).thenReturn(new Point(100, 100));
        when(drop.isPickedUp()).thenReturn(false);
        when(drop.canBePickedBy(any(Character.class))).thenReturn(true);
        when(drop.getDropTime()).thenReturn(dropTime);
        when(drop.getOwnerId()).thenReturn(ownerId);
        when(drop.isPlayerDrop()).thenReturn(playerDrop);
        when(drop.getItemId()).thenReturn(0);
        when(drop.getMeso()).thenReturn(1);
        when(drop.getQuest()).thenReturn(0);
        return drop;
    }
}
