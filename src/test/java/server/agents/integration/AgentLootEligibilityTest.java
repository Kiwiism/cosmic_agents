package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import server.agents.capabilities.looting.AgentLootEligibility;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.runtime.AgentSessionLifecycleRuntime;
import server.maps.MapItem;
import server.maps.MapleMap;
import scripting.event.EventInstanceManager;
import server.agents.capabilities.expedition.balrog.AgentBalrogDefinition;
import server.agents.capabilities.expedition.balrog.AgentEasyBalrogRewardGracePolicy;
import server.agents.capabilities.expedition.balrog.AgentEasyBalrogRewardClaimRegistry;

import java.awt.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentLootEligibilityTest {
    @Test
    void easyBalrogRewardDropsRemainHumanOnlyForSevenSecondsAfterTheSpray() {
        long openedAt = 10_000L;
        Character bot = mock(Character.class);
        EventInstanceManager event = mock(EventInstanceManager.class);
        when(bot.getMapId()).thenReturn(AgentBalrogDefinition.CLEAR_MAP);
        when(bot.getEventInstance()).thenReturn(event);
        when(event.getProperty(AgentEasyBalrogRewardGracePolicy.REWARD_OPENED_AT_PROPERTY))
                .thenReturn(Long.toString(openedAt));

        assertTrue(AgentEasyBalrogRewardGracePolicy.blocksAgentLoot(bot, openedAt + 6_999L));
        assertFalse(AgentEasyBalrogRewardGracePolicy.blocksAgentLoot(bot, openedAt + 7_000L));
    }

    @Test
    void easyBalrogAgentsCannotLootBeforeTheRewardReactorSpraysItems() {
        Character bot = mock(Character.class);
        EventInstanceManager event = mock(EventInstanceManager.class);
        when(bot.getMapId()).thenReturn(AgentBalrogDefinition.CLEAR_MAP);
        when(bot.getEventInstance()).thenReturn(event);

        assertTrue(AgentEasyBalrogRewardGracePolicy.blocksAgentLoot(bot, 10_000L));
    }

    @Test
    void easyBalrogRewardClaimsPreventOneAgentFromTakingAnotherAgentsShare() {
        long openedAt = 10_000L;
        MapleMap map = mock(MapleMap.class);
        Character assigned = mock(Character.class);
        Character other = mock(Character.class);
        EventInstanceManager event = mock(EventInstanceManager.class);
        MapItem drop = mockLoot(77, 0, false, openedAt);
        when(assigned.getId()).thenReturn(10);
        when(other.getId()).thenReturn(20);
        for (Character bot : List.of(assigned, other)) {
            when(bot.getMapId()).thenReturn(AgentBalrogDefinition.CLEAR_MAP);
            when(bot.getMap()).thenReturn(map);
            when(bot.getEventInstance()).thenReturn(event);
            when(bot.getPosition()).thenReturn(new Point(100, 100));
        }
        when(event.getProperty(AgentEasyBalrogRewardGracePolicy.REWARD_OPENED_AT_PROPERTY))
                .thenReturn(Long.toString(openedAt));
        AgentEasyBalrogRewardClaimRegistry.replace(map, java.util.Map.of(77, 10));

        assertTrue(AgentEasyBalrogRewardGracePolicy.permitsAgentLoot(
                assigned, drop, openedAt + 7_000L));
        assertFalse(AgentEasyBalrogRewardGracePolicy.permitsAgentLoot(
                other, drop, openedAt + 7_000L));
        AgentEasyBalrogRewardClaimRegistry.clear(map);
    }

    @Test
    void easyBalrogAssignedAgentMustStillWalkIntoPhysicalPickupRange() {
        long openedAt = 10_000L;
        MapleMap map = mock(MapleMap.class);
        Character assigned = mock(Character.class);
        EventInstanceManager event = mock(EventInstanceManager.class);
        MapItem drop = mockLoot(77, 0, false, openedAt);
        when(assigned.getId()).thenReturn(10);
        when(assigned.getMapId()).thenReturn(AgentBalrogDefinition.CLEAR_MAP);
        when(assigned.getMap()).thenReturn(map);
        when(assigned.getEventInstance()).thenReturn(event);
        when(assigned.getPosition()).thenReturn(new Point(60, 100));
        when(event.getProperty(AgentEasyBalrogRewardGracePolicy.REWARD_OPENED_AT_PROPERTY))
                .thenReturn(Long.toString(openedAt));
        AgentEasyBalrogRewardClaimRegistry.replace(map, java.util.Map.of(77, 10));

        assertFalse(AgentEasyBalrogRewardGracePolicy.permitsAgentLoot(
                assigned, drop, openedAt + 7_000L));
        when(assigned.getPosition()).thenReturn(new Point(68, 100));
        assertTrue(AgentEasyBalrogRewardGracePolicy.permitsAgentLoot(
                assigned, drop, openedAt + 7_000L));
        AgentEasyBalrogRewardClaimRegistry.clear(map);
    }
    @Test
    void shouldDelayTargetingBotInventoryDropsForFifteenSeconds() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mock(Character.class);
        MapItem drop = mockLoot(1, 99, true, System.currentTimeMillis() - 14_000L);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);
        Character dropBotOwner = mock(Character.class);

        when(bot.getId()).thenReturn(88);
        doReturn(drop).when(map).getMapObject(1);

        try (MockedStatic<AgentSessionLifecycleRuntime> lifecycle =
                     mockStatic(AgentSessionLifecycleRuntime.class)) {
            lifecycle.when(() -> AgentSessionLifecycleRuntime.activeLeaderByAgentCharacterId(99))
                    .thenReturn(dropBotOwner);

            assertFalse(AgentLootEligibility.canBotTargetLoot(entry, bot, map, drop, System.currentTimeMillis()));
        }
    }

    @Test
    void shouldAllowTargetingBotInventoryDropsAfterFifteenSeconds() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mock(Character.class);
        MapItem drop = mockLoot(1, 99, true, System.currentTimeMillis() - 16_000L);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);
        Character dropBotOwner = mock(Character.class);

        when(bot.getId()).thenReturn(88);
        doReturn(drop).when(map).getMapObject(1);

        try (MockedStatic<AgentSessionLifecycleRuntime> lifecycle =
                     mockStatic(AgentSessionLifecycleRuntime.class)) {
            lifecycle.when(() -> AgentSessionLifecycleRuntime.activeLeaderByAgentCharacterId(99))
                    .thenReturn(dropBotOwner);

            assertTrue(AgentLootEligibility.canBotTargetLoot(entry, bot, map, drop, System.currentTimeMillis()));
        }
    }

    @Test
    void shouldDelayTargetingOwnBotInventoryDropsForFifteenSeconds() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        MapItem drop = mockLoot(1, 88, true, System.currentTimeMillis() - 14_000L);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);

        when(bot.getId()).thenReturn(88);
        doReturn(drop).when(map).getMapObject(1);

        try (MockedStatic<AgentSessionLifecycleRuntime> lifecycle =
                     mockStatic(AgentSessionLifecycleRuntime.class)) {
            lifecycle.when(() -> AgentSessionLifecycleRuntime.activeLeaderByAgentCharacterId(88))
                    .thenReturn(owner);

            assertFalse(AgentLootEligibility.canBotTargetLoot(entry, bot, map, drop, System.currentTimeMillis()));
        }
    }

    @Test
    void shouldKeepExistingThreeSecondTargetDelayForHumanInventoryDrops() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mock(Character.class);
        MapItem drop = mockLoot(1, 77, true, System.currentTimeMillis() - 4_000L);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);

        when(bot.getId()).thenReturn(88);
        doReturn(drop).when(map).getMapObject(1);

        try (MockedStatic<AgentSessionLifecycleRuntime> lifecycle =
                     mockStatic(AgentSessionLifecycleRuntime.class)) {
            lifecycle.when(() -> AgentSessionLifecycleRuntime.activeLeaderByAgentCharacterId(77))
                    .thenReturn(null);

            assertTrue(AgentLootEligibility.canBotTargetLoot(entry, bot, map, drop, System.currentTimeMillis()));
        }
    }

    @Test
    void shouldAllowMobLootWhenBasePickupEligibilityAllowsIt() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        MapItem drop = mockLoot(1, 77, false, System.currentTimeMillis() - 16_000L);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);

        when(bot.getId()).thenReturn(88);
        when(owner.getId()).thenReturn(10);
        doReturn(drop).when(map).getMapObject(1);

        assertTrue(AgentLootEligibility.canBotTargetLoot(entry, bot, map, drop, System.currentTimeMillis()));
    }

    @Test
    void recentMeleeKillCanUseShorterTargetDelayWithoutBypassingPickupFloor() {
        long now = System.currentTimeMillis();
        MapleMap map = mock(MapleMap.class);
        Character bot = mock(Character.class);
        MapItem eligibleDrop = mockLoot(1, 77, false, now - 800L);
        MapItem tooFreshDrop = mockLoot(2, 77, false, now - 300L);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);

        doReturn(eligibleDrop).when(map).getMapObject(1);
        doReturn(tooFreshDrop).when(map).getMapObject(2);

        assertFalse(AgentLootEligibility.canBotTargetLoot(entry, bot, map, eligibleDrop, now));
        assertTrue(AgentLootEligibility.canBotTargetLoot(
                entry, bot, map, eligibleDrop, now, 750L));
        assertFalse(AgentLootEligibility.canBotTargetLoot(
                entry, bot, map, tooFreshDrop, now, 100L));
    }

    @Test
    void shouldRejectMobLootWhenBasePickupEligibilityRejectsIt() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        MapItem drop = mockLoot(1, 77, false, System.currentTimeMillis() - 16_000L);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);

        when(bot.getId()).thenReturn(88);
        when(owner.getId()).thenReturn(10);
        when(drop.canBePickedBy(bot)).thenReturn(false);
        doReturn(drop).when(map).getMapObject(1);

        assertFalse(AgentLootEligibility.canBotTargetLoot(entry, bot, map, drop, System.currentTimeMillis()));
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
