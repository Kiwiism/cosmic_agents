package server.agents.integration;

import server.agents.integration.AgentMovementStateRuntime;

import server.agents.integration.AgentClimbStateRuntime;

import server.agents.integration.AgentMovementPhysicsStateRuntime;

import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentFootholdIndexService;
import server.agents.capabilities.navigation.AgentNavigationPathService;
import server.agents.capabilities.navigation.AgentNavigationRegionService;

import server.agents.capabilities.navigation.AgentNavigationGraph;

import server.agents.capabilities.trade.AgentOfferService;

import server.agents.capabilities.supplies.AgentPotionService;

import server.agents.capabilities.combat.AgentAttackRoute;

import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentAttackPlan;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentGrindTargetSearchPolicy;
import server.agents.capabilities.combat.AgentGrindNavigationTargetSelector;
import server.agents.capabilities.combat.AgentRangedPriorityTargetSelector;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy;
import server.agents.capabilities.looting.AgentLootTargetService;
import server.agents.capabilities.dialogue.AgentItemQueryNormalizer;
import server.agents.capabilities.trade.AgentOwnerItemNotificationService;

import server.agents.integration.AgentCombatAttackRuntime;
import server.agents.integration.AgentCombatPlanRuntime;
import server.agents.integration.AgentCombatSkillCacheStateRuntime;
import server.agents.integration.AgentOwnerMotionStateRuntime;
import server.agents.runtime.AgentFollowIdleMovementRuntime;
import server.agents.runtime.AgentGrindTargetRuntime;
import server.agents.runtime.AgentMovementOnlyStepRuntime;
import server.agents.runtime.AgentRuntimeCleanupService;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentRuntimeConfig;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSpawnPlacementRuntime;
import server.agents.runtime.AgentTargetSnapshot;
import server.agents.runtime.AgentTargetSnapshotRuntime;
import server.agents.runtime.AgentTickFailureRuntime;
import client.Character;
import client.BuffStat;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import client.keybind.KeyBinding;
import constants.game.CharacterStance;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.dialogue.AgentChatCommandClassifier;
import server.agents.capabilities.dialogue.AgentTradeDialogueClassifier;
import server.agents.capabilities.supplies.AgentAmmoService;
import server.agents.capabilities.supplies.AgentAmmoDonorPlan;
import server.agents.commands.AgentTargetedCommandMatch;
import server.agents.commands.AgentTransferCommand;
import server.agents.integration.AgentCommandTargetResolver;
import server.agents.integration.AgentBreakoutStateRuntime;
import server.agents.integration.AgentFarmAnchorStateRuntime;
import server.agents.integration.AgentGrindLootStateRuntime;
import server.agents.integration.AgentGrindSearchStateRuntime;
import server.agents.integration.AgentGrindTargetStateRuntime;
import server.agents.integration.AgentGrindWanderStateRuntime;
import server.agents.integration.AgentMapStateRuntime;
import server.agents.integration.AgentModeStateRuntime;
import server.agents.integration.AgentMovementCommandRuntime;
import server.agents.integration.AgentMoveTargetStateRuntime;
import server.agents.integration.AgentNavigationDebugStateRuntime;
import server.agents.integration.AgentPendingActionStateRuntime;
import server.agents.integration.AgentPendingTradeStateRuntime;
import server.agents.integration.AgentPqRuntime;
import server.agents.integration.AgentShopStateRuntime;
import server.StatEffect;
import server.TimerManager;
import server.life.Monster;
import server.maps.Foothold;
import server.maps.FootholdTree;
import server.maps.MapItem;
import server.maps.MapleMap;
import server.maps.Rope;
import testutil.Items;

import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotManagerTest {
    @Test
    void shouldParseTransferBotCommands() {
        AgentTransferCommand command = AgentCommandTargetResolver.matchAgentTransferCommand("transfer Jason to Bob");

        assertNotNull(command);
        assertEquals("Jason", command.botName());
        assertEquals("Bob", command.targetName());
    }

    @Test
    void shouldStillAllowTransferWithoutTo() {
        AgentTransferCommand command = AgentCommandTargetResolver.matchAgentTransferCommand("transfer Jason Bob");

        assertNotNull(command);
        assertEquals("Jason", command.botName());
        assertEquals("Bob", command.targetName());
    }

    @Test
    void shouldNotTreatGivePhrasesAsBotTransfers() {
        assertNull(AgentCommandTargetResolver.matchAgentTransferCommand("give Jason Bob"));
        assertNull(AgentCommandTargetResolver.matchAgentTransferCommand("give me flaming feather"));
        assertNull(AgentCommandTargetResolver.matchAgentTransferCommand("give flaming feather"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSkipOwnerGainOfferScanForOwnBotTradeItems() throws Exception {
        Character owner = mock(Character.class);
        Character sourceBot = mock(Character.class);
        Character observerBot = mock(Character.class);
        AgentRuntimeEntry sourceEntry = new AgentRuntimeEntry(sourceBot, owner, null);
        AgentRuntimeEntry observerEntry = new AgentRuntimeEntry(observerBot, owner, null);
        Item tradedEquip = new Item(1002000, (short) 1, (short) 1);

        when(owner.getId()).thenReturn(77);
        when(sourceBot.getId()).thenReturn(10);
        when(sourceBot.getClient()).thenReturn(new client.BotClient(0, 0));
        when(observerBot.getId()).thenReturn(11);

        Map<Integer, List<AgentRuntimeEntry>> bots = AgentRuntimeRegistry.entriesByLeaderId();
        bots.put(owner.getId(), List.of(sourceEntry, observerEntry));

        try (MockedStatic<AgentOfferService> offers = mockStatic(AgentOfferService.class)) {
            AgentOwnerItemNotificationService.notifyOwnerGainedTradeItem(owner, tradedEquip, sourceBot);

            offers.verifyNoInteractions();
        } finally {
            bots.remove(owner.getId());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotifyBotsForNonOwnBotTradeItems() throws Exception {
        Character owner = mock(Character.class);
        Character observerBot = mock(Character.class);
        Character sourcePlayer = mock(Character.class);
        AgentRuntimeEntry observerEntry = new AgentRuntimeEntry(observerBot, owner, null);
        Item tradedEquip = new Item(1002000, (short) 1, (short) 1);

        when(owner.getId()).thenReturn(78);

        Map<Integer, List<AgentRuntimeEntry>> bots = AgentRuntimeRegistry.entriesByLeaderId();
        bots.put(owner.getId(), List.of(observerEntry));

        TimerManager inlineTimer = mock(TimerManager.class);
        when(inlineTimer.schedule(any(Runnable.class), anyLong())).thenAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        });
        try (MockedStatic<AgentOfferService> offers = mockStatic(AgentOfferService.class);
             MockedStatic<TimerManager> timer = mockStatic(TimerManager.class)) {
            timer.when(TimerManager::getInstance).thenReturn(inlineTimer);

            AgentOwnerItemNotificationService.notifyOwnerGainedTradeItem(owner, tradedEquip, sourcePlayer);

            offers.verify(() -> AgentOfferService.notifyOwnerGainedEquip(observerEntry, observerBot, tradedEquip));
        } finally {
            bots.remove(owner.getId());
        }
    }

    @Test
    void shouldresolveTargetedAgentByPrefix() {
        AgentRuntimeEntry jason = botEntryNamed("Jason");
        AgentRuntimeEntry bob = botEntryNamed("Bob");

        AgentTargetedCommandMatch<AgentRuntimeEntry> match = AgentCommandTargetResolver.resolveTargetedAgent(
                List.of(jason, bob), "Ja pots?");

        assertEquals(jason, match.entry());
        assertEquals("pots?", match.commandText());
        assertNull(match.feedbackMessage());
    }

    @Test
    void shouldresolveTargetedAgentBySlot() {
        AgentRuntimeEntry jason = botEntryNamed("Jason");
        AgentRuntimeEntry bob = botEntryNamed("Bob");

        AgentTargetedCommandMatch<AgentRuntimeEntry> match = AgentCommandTargetResolver.resolveTargetedAgent(
                List.of(jason, bob), "2 follow Alice");

        assertEquals(bob, match.entry());
        assertEquals("follow Alice", match.commandText());
        assertNull(match.feedbackMessage());
    }

    @Test
    void shouldReturnFeedbackForAmbiguousTargetedBotPrefix() {
        AgentRuntimeEntry jane = botEntryNamed("Jane");
        AgentRuntimeEntry jason = botEntryNamed("Jason");

        AgentTargetedCommandMatch<AgentRuntimeEntry> match = AgentCommandTargetResolver.resolveTargetedAgent(
                List.of(jane, jason), "Ja yes");

        assertNull(match.entry());
        assertNull(match.commandText());
        assertEquals("Ambiguous bot prefix 'Ja': 1: Jane, 2: Jason. Use the full name or a slot number.",
                match.feedbackMessage());
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

        int[] counts = AgentPotionService.countPotions(
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
        AgentNavigationGraphService.rebuildGraph(map);

        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        assertTrue(AgentGrindNavigationTargetSelector.shouldUseLocalCombatRetreatTarget(
                entry,
                new Point(100, 100),
                new Point(130, 100),
                new Point(60, 100),
                grindNavigationHooks()));
    }

    @Test
    void shouldRejectCombatRetreatTargetWhenMonsterIsInDifferentRegion() {
        MapleMap map = createEmptyTestMap(910000021);
        FootholdTree footholds = map.getFootholds();
        footholds.insert(new Foothold(new Point(0, 100), new Point(200, 100), 1));
        footholds.insert(new Foothold(new Point(0, 40), new Point(200, 40), 2));
        AgentNavigationGraphService.rebuildGraph(map);

        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        assertFalse(AgentGrindNavigationTargetSelector.shouldUseLocalCombatRetreatTarget(
                entry,
                new Point(100, 100),
                new Point(100, 40),
                new Point(60, 100),
                grindNavigationHooks()));
    }

    @Test
    void shouldRejectCombatRetreatTargetWhileClimbing() {
        MapleMap map = createEmptyTestMap(910000022);
        FootholdTree footholds = map.getFootholds();
        footholds.insert(new Foothold(new Point(0, 100), new Point(200, 100), 1));
        footholds.insert(new Foothold(new Point(0, 40), new Point(200, 40), 2));
        map.addRope(new Rope(100, 40, 100, false));
        AgentNavigationGraphService.rebuildGraph(map);

        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentClimbStateRuntime.setClimbingOnRope(entry, new Rope(100, 40, 100, false));

        assertFalse(AgentGrindNavigationTargetSelector.shouldUseLocalCombatRetreatTarget(
                entry,
                new Point(100, 100),
                new Point(140, 40),
                new Point(60, 100),
                grindNavigationHooks()));
    }

    @Test
    void shouldRecoverGrindingBotToInBoundsOwnerWhenOutOfBounds() {
        MapleMap map = createEmptyTestMap(910000052);
        map.setMapLineBoundings(-500, 500, -500, 500);
        map.getFootholds().insert(new Foothold(new Point(0, 100), new Point(200, 100), 1));
        Character owner = mockMovingBot(new Point(100, 100), map);
        Character bot = mockMovingBot(new Point(100, 1700), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);
        AgentModeStateRuntime.setGrinding(entry, true);

        AgentMovementOnlyStepRuntime.stepMovementOnly(entry, bot.getPosition(), true);

        assertEquals(new Point(100, 100), bot.getPosition());
        assertFalse(AgentMovementStateRuntime.inAir(entry));
        assertFalse(AgentClimbStateRuntime.climbing(entry));
    }

    @Test
    void shouldNotUseLowerPlatformDropAsCrossRegionRetreat() {
        MapleMap map = createEmptyTestMap(910000060);
        FootholdTree footholds = map.getFootholds();
        footholds.insert(new Foothold(new Point(0, 100), new Point(500, 100), 1));
        footholds.insert(new Foothold(new Point(0, 220), new Point(500, 220), 2));
        AgentNavigationGraphService.rebuildGraph(map);

        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(map);
        when(bot.getPosition()).thenReturn(new Point(250, 100));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        assertNull(AgentGrindNavigationTargetSelector.selectCrossRegionRetreatTarget(
                entry,
                new Point(250, 100),
                new Point(300, 100),
                grindNavigationHooks()));
    }

    @Test
    void shouldUseJumpReachablePlatformAsCrossRegionRetreat() {
        MapleMap map = createEmptyTestMap(910000061);
        FootholdTree footholds = map.getFootholds();
        footholds.insert(new Foothold(new Point(0, 100), new Point(200, 100), 1));
        footholds.insert(new Foothold(new Point(250, 100), new Point(500, 100), 2));
        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map);

        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(map);
        when(bot.getPosition()).thenReturn(new Point(300, 100));
        when(bot.getSkills()).thenReturn(Map.of());
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        Point retreat = AgentGrindNavigationTargetSelector.selectCrossRegionRetreatTarget(
                entry,
                new Point(300, 100),
                new Point(330, 100),
                grindNavigationHooks());

        assertNotNull(retreat);
        assertTrue(retreat.x <= 200);
        assertTrue(Math.abs(retreat.x - 330) > AgentCombatConfig.cfg.RANGED_DEGENERATE_RANGE_X);

        int startRegionId = AgentNavigationRegionService.resolveCurrentRegionId(graph, entry, map, new Point(300, 100));
        int retreatRegionId = AgentNavigationRegionService.resolveTargetRegionId(graph, entry, map, retreat);
        List<AgentNavigationGraph.Edge> path = AgentNavigationPathService.findPath(
                graph, map, new Point(300, 100), startRegionId, retreatRegionId, retreat);
        assertFalse(path.isEmpty());
        assertEquals(AgentNavigationGraph.EdgeType.JUMP, path.get(0).type);
    }

    @Test
    void shouldPreferRangedAttackTargetOverDegeneratePreferredTarget() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        Point botPos = new Point(100, 100);
        Monster closeMob = mockMob(new Point(150, 100), 9300400);
        Monster rangedMob = mockMob(new Point(260, 100), 9300401);
        AgentAttackPlan rangedPlan = new AgentAttackPlan(
                0, 0, 1, new Rectangle(105, 50, 395, 100),
                List.of(rangedMob), AgentAttackRoute.RANGED,
                0, 11, 11, 11, 4, 300, 600, null);

        when(bot.getMap()).thenReturn(map);
        when(map.getAllMonsters()).thenReturn(List.of(closeMob, rangedMob));

        try (MockedStatic<AgentAttackExecutionProvider> attacks =
                     mockStatic(AgentAttackExecutionProvider.class, org.mockito.Mockito.CALLS_REAL_METHODS);
             MockedStatic<AgentCombatPlanRuntime> plans =
                     mockStatic(AgentCombatPlanRuntime.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.BOW);
            plans.when(() -> AgentCombatPlanRuntime.planAttack(entry, bot, rangedMob, AgentCombatConfig.cfg))
                    .thenReturn(rangedPlan);

            assertEquals(rangedMob, AgentRangedPriorityTargetSelector.selectPriorityRangedAttackTarget(
                    entry, bot, botPos, closeMob));
        }
    }

    @Test
    void shouldKeepMovingWhenInRangeRangedAttackDoesNotFire() {
        MapleMap map = createEmptyTestMap(910000062);
        map.getFootholds().insert(new Foothold(new Point(-200, 100), new Point(200, 100), 1));
        Character bot = mockMovingBot(new Point(100, 100), map);
        Monster target = mockMob(new Point(-50, 100), 9300500);
        when(target.getMap()).thenReturn(map);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setGrinding(entry, true);
        AgentGrindTargetStateRuntime.setTarget(entry, target);
        AgentMapStateRuntime.setMapTracking(entry, map.getId(), AgentFootholdIndexService.buildFhIndex(map));
        AgentAttackPlan rangedPlan = new AgentAttackPlan(
                0, 0, 1, new Rectangle(-200, 50, 300, 100),
                List.of(target), AgentAttackRoute.RANGED,
                0, 11, 11, 11, 4, 300, 600, null);

        try (MockedStatic<AgentAttackExecutionProvider> attacks =
                     mockStatic(AgentAttackExecutionProvider.class, org.mockito.Mockito.CALLS_REAL_METHODS);
             MockedStatic<AgentCombatPlanRuntime> plans =
                     mockStatic(AgentCombatPlanRuntime.class, org.mockito.Mockito.CALLS_REAL_METHODS);
             MockedStatic<AgentCombatAttackRuntime> attacksRuntime =
                     mockStatic(AgentCombatAttackRuntime.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.CLAW);
            plans.when(() -> AgentCombatPlanRuntime.planAttack(entry, bot, target, AgentCombatConfig.cfg))
                    .thenReturn(rangedPlan);
            attacksRuntime.when(() -> AgentCombatAttackRuntime.attackMonster(entry, bot, rangedPlan))
                    .thenAnswer(invocation -> null);

            AgentMovementOnlyStepRuntime.stepMovementOnly(entry, target.getPosition(), false);
        }

        assertTrue(bot.getPosition().x < 100);
    }

    @Test
    void shouldCommitToBreakoutDirectionWhenSurroundedDespiteTargetSwap() {
        MapleMap map = spy(createEmptyTestMap(910000063));
        map.getFootholds().insert(new Foothold(new Point(-500, 100), new Point(500, 100), 1));
        AgentNavigationGraphService.rebuildGraph(map);
        Character bot = mock(Character.class);
        Point botPos = new Point(100, 100);
        when(bot.getMap()).thenReturn(map);
        when(bot.getPosition()).thenReturn(new Point(botPos));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        // Pincer: a mob inside the retreat band on each side (dx 60 <= 80, dy 0 <= 50).
        Monster leftMob = mockMob(new Point(40, 100), 9300600);
        Monster rightMob = mockMob(new Point(160, 100), 9300601);
        doReturn(List.of(leftMob, rightMob)).when(map).getAllMonsters();

        try (MockedStatic<AgentAttackExecutionProvider> attacks =
                     mockStatic(AgentAttackExecutionProvider.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.BOW);

            Point first = AgentGrindNavigationTargetSelector.selectGrindNavigationTarget(entry, botPos, rightMob.getPosition(), grindNavigationHooks());
            int dir1 = Integer.signum(first.x - botPos.x);
            assertTrue(dir1 != 0);
            assertTrue(AgentBreakoutStateRuntime.hasBreakoutCommitment(entry));

            // Crowding-swap points the active target at the OTHER flank on later ticks; the
            // committed breakout must not reverse (that flip is the oscillation we are fixing).
            Point second = AgentGrindNavigationTargetSelector.selectGrindNavigationTarget(entry, botPos, leftMob.getPosition(), grindNavigationHooks());
            assertEquals(dir1, Integer.signum(second.x - botPos.x));
            Point third = AgentGrindNavigationTargetSelector.selectGrindNavigationTarget(entry, botPos, rightMob.getPosition(), grindNavigationHooks());
            assertEquals(dir1, Integer.signum(third.x - botPos.x));
        }
    }

    @Test
    void shouldClearBreakoutOnceNoLongerSurrounded() {
        MapleMap map = spy(createEmptyTestMap(910000064));
        map.getFootholds().insert(new Foothold(new Point(-500, 100), new Point(500, 100), 1));
        AgentNavigationGraphService.rebuildGraph(map);
        Character bot = mock(Character.class);
        Point botPos = new Point(100, 100);
        when(bot.getMap()).thenReturn(map);
        when(bot.getPosition()).thenReturn(new Point(botPos));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentBreakoutStateRuntime.setBreakoutCommitment(entry, -1, System.currentTimeMillis() + 5_000L);

        // Only one flank occupied -> not surrounded -> the breakout latch must release.
        Monster rightMob = mockMob(new Point(160, 100), 9300602);
        doReturn(List.of(rightMob)).when(map).getAllMonsters();

        try (MockedStatic<AgentAttackExecutionProvider> attacks =
                     mockStatic(AgentAttackExecutionProvider.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.BOW);

            AgentGrindNavigationTargetSelector.selectGrindNavigationTarget(entry, botPos, rightMob.getPosition(), grindNavigationHooks());
            assertFalse(AgentBreakoutStateRuntime.hasBreakoutCommitment(entry));
        }
    }

    @Test
    void shouldNotEngageBreakoutForSingleMobKiting() {
        MapleMap map = spy(createEmptyTestMap(910000065));
        map.getFootholds().insert(new Foothold(new Point(-500, 100), new Point(500, 100), 1));
        AgentNavigationGraphService.rebuildGraph(map);
        Character bot = mock(Character.class);
        Point botPos = new Point(100, 100);
        when(bot.getMap()).thenReturn(map);
        when(bot.getPosition()).thenReturn(new Point(botPos));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        Monster rightMob = mockMob(new Point(160, 100), 9300603);
        doReturn(List.of(rightMob)).when(map).getAllMonsters();

        try (MockedStatic<AgentAttackExecutionProvider> attacks =
                     mockStatic(AgentAttackExecutionProvider.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.BOW);

            Point nav = AgentGrindNavigationTargetSelector.selectGrindNavigationTarget(entry, botPos, rightMob.getPosition(), grindNavigationHooks());
            // Single mob -> ordinary local kiting (retreat away from the mob), no breakout latch.
            assertFalse(AgentBreakoutStateRuntime.hasBreakoutCommitment(entry));
            assertEquals(AgentAttackExecutionProvider.retreatTargetPosition(bot, botPos, rightMob.getPosition()), nav);
        }
    }

    @Test
    void shouldResetPhysicsWhenOnlineBotIsSpawnedAtOwnerPosition() {
        MapleMap map = createEmptyTestMap(910000023);
        map.getFootholds().insert(new Foothold(new Point(0, 100), new Point(200, 100), 1));
        Character bot = mockMovingBot(new Point(20, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);
        AgentMovementStateRuntime.setInAir(entry, true);
        AgentMovementPhysicsStateRuntime.setPhysicsX(entry, -999);
        AgentMovementPhysicsStateRuntime.setPhysicsY(entry, -999);
        AgentMovementPhysicsStateRuntime.setVerticalVelocity(entry, 20f);
        AgentMovementPhysicsStateRuntime.setAirVelocityX(entry, 6);
        AgentNavigationDebugStateRuntime.setNavTargetPosition(entry, new Point(120, 100));

        AgentSpawnPlacementRuntime.placeSpawnedOnlineAgent(entry, bot, map, new Point(80, 100));

        assertEquals(new Point(80, 100), bot.getPosition());
        assertFalse(AgentMovementStateRuntime.inAir(entry));
        assertEquals(80.0, AgentMovementPhysicsStateRuntime.physicsX(entry));
        assertEquals(100.0, AgentMovementPhysicsStateRuntime.physicsY(entry));
        assertEquals(0, AgentMovementPhysicsStateRuntime.airVelocityX(entry));
        assertNull(AgentNavigationDebugStateRuntime.navTargetPosition(entry));
        assertEquals(map.getId(), AgentMapStateRuntime.lastMapId(entry));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCleanBotRuntimeStateWhenLeavingBotControl() throws Exception {
        Character owner = mock(Character.class);
        Character bot = mock(Character.class);
        ScheduledFuture<?> task = mock(ScheduledFuture.class);
        Map<Integer, KeyBinding> keymap = new LinkedHashMap<>();
        keymap.put(91, new KeyBinding(2, 2000002));
        keymap.put(92, new KeyBinding(7, 2000003));

        when(owner.getId()).thenReturn(77);
        when(bot.getId()).thenReturn(88);
        when(bot.getKeymap()).thenReturn(keymap);
        doAnswer(invocation -> {
            int key = invocation.getArgument(0);
            KeyBinding binding = invocation.getArgument(1);
            if (binding.getType() == 0) {
                keymap.remove(key);
            } else {
                keymap.put(key, binding);
            }
            return null;
        }).when(bot).changeKeybinding(anyInt(), any(KeyBinding.class));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, task);
        Map<Integer, List<AgentRuntimeEntry>> bots = AgentRuntimeRegistry.entriesByLeaderId();
        bots.put(owner.getId(), new CopyOnWriteArrayList<>(List.of(entry)));
        try {
            assertTrue(AgentRuntimeCleanupService.cleanupAgentRuntimeState(bot));

            assertFalse(bots.containsKey(owner.getId()));
            assertEquals(7, keymap.get(91).getType());
            assertEquals(2000002, keymap.get(91).getAction());
            assertEquals(7, keymap.get(92).getType());
            assertEquals(2000003, keymap.get(92).getAction());
            verify(task).cancel(false);
            verify(bot).setAutopotHpAlert(0f);
            verify(bot).setAutopotMpAlert(0f);
        } finally {
            bots.remove(owner.getId());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDisableBotAfterRepeatedTickFailures() throws Exception {
        Character owner = mock(Character.class);
        Character bot = mock(Character.class);
        ScheduledFuture<?> task = mock(ScheduledFuture.class);

        when(owner.getId()).thenReturn(77);
        when(owner.getName()).thenReturn("Owner");
        when(bot.getId()).thenReturn(88);
        when(bot.getName()).thenReturn("Bot");
        when(bot.getMapId()).thenReturn(100000000);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, task);
        AgentPendingActionStateRuntime.setPendingAction(entry, "drop");
        AgentPendingActionStateRuntime.setPendingDropCategory(entry, "equips");
        AgentGrindLootStateRuntime.setGrindLootTarget(entry, mock(MapItem.class));
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentModeStateRuntime.setGrinding(entry, true);
        AgentMoveTargetStateRuntime.setMoveTarget(entry, new Point(100, 100), false);

        Map<Integer, List<AgentRuntimeEntry>> bots = AgentRuntimeRegistry.entriesByLeaderId();
        bots.put(owner.getId(), new CopyOnWriteArrayList<>(List.of(entry)));
        try {
            AgentTickFailureRuntime.handleFailure(entry, owner.getId(), bot.getId(), new NullPointerException("bad drop"));
            assertTrue(bots.containsKey(owner.getId()));
            assertNull(AgentPendingActionStateRuntime.pendingAction(entry));
            assertNull(AgentPendingActionStateRuntime.pendingDropCategory(entry));
            assertNull(AgentGrindLootStateRuntime.grindLootTarget(entry));
            assertTrue(AgentModeStateRuntime.following(entry));
            assertTrue(AgentModeStateRuntime.grinding(entry));

            AgentTickFailureRuntime.handleFailure(entry, owner.getId(), bot.getId(), new NullPointerException("bad drop"));
            assertTrue(bots.containsKey(owner.getId()));
            assertFalse(AgentModeStateRuntime.following(entry));
            assertFalse(AgentModeStateRuntime.grinding(entry));
            assertNull(AgentMoveTargetStateRuntime.moveTarget(entry));

            AgentTickFailureRuntime.handleFailure(entry, owner.getId(), bot.getId(), new NullPointerException("bad drop"));
            assertFalse(bots.containsKey(owner.getId()));
            verify(task).cancel(false);
        } finally {
            bots.remove(owner.getId());
        }
    }

    @Test
    void shouldUseFollowIdleFastPathOnlyWhileParkedNearTarget() {
        MapleMap map = createEmptyTestMap(910000024);
        map.getFootholds().insert(new Foothold(new Point(0, 100), new Point(200, 100), 1));
        Character bot = mockMovingBot(new Point(80, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);
        AgentModeStateRuntime.setFollowing(entry, true);

        assertTrue(AgentFollowIdleMovementRuntime.tryFollowIdleMovementFastPath(entry, bot, new Point(100, 100), 1_000L));
        assertEquals("idle-fast", AgentNavigationDebugStateRuntime.lastDecision(entry));
        assertTrue(AgentFollowIdleMovementRuntime.tryFollowIdleMovementFastPath(entry, bot, new Point(100, 100), 1_500L),
                "idle follow bots should skip per-tick nav/ground movement between periodic checks");
        assertFalse(AgentFollowIdleMovementRuntime.tryFollowIdleMovementFastPath(entry, bot, new Point(100, 100), 2_000L),
                "idle fast path should allow a periodic full movement/nav check");

        AgentOwnerMotionStateRuntime.rememberOwnerPosition(entry, new Point(0, 0));
        AgentOwnerMotionStateRuntime.updateObservedOwnerStep(entry, new Point(1, 0));
        assertFalse(AgentFollowIdleMovementRuntime.tryFollowIdleMovementFastPath(entry, bot, new Point(100, 100), 2_100L),
                "owner movement should force normal movement resolution");
    }

    @Test
    void shouldKeepAttackableGrindTargetInsteadOfRetargetingDuringCooldown() {
        MapleMap map = createEmptyTestMap(910000028);
        Character bot = mockMovingBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);
        AgentGrindSearchStateRuntime.scheduleNextSearch(entry, 1_000L);
        Monster target = mock(Monster.class);
        when(target.getPosition()).thenReturn(new Point(140, 100));
        AgentAttackPlan plan = basicClosePlan(target);

        assertFalse(AgentGrindTargetSearchPolicy.shouldSearchForGrindTarget(entry, bot, target, plan, 1_000L));
    }

    @Test
    void shouldRetargetWhenCurrentGrindTargetIsNotAttackableAndIntervalElapsed() {
        MapleMap map = createEmptyTestMap(910000029);
        Character bot = mockMovingBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);
        AgentGrindSearchStateRuntime.scheduleNextSearch(entry, 1_000L);
        Monster target = mock(Monster.class);
        when(target.getPosition()).thenReturn(new Point(300, 100));
        AgentAttackPlan plan = basicClosePlan(target);

        assertTrue(AgentGrindTargetSearchPolicy.shouldSearchForGrindTarget(entry, bot, target, plan, 1_000L));
    }

    @Test
    void shouldKeepScanningForClusterWhenAoeBotSingleTargetsInRange() {
        MapleMap map = createEmptyTestMap(910000128);
        Character bot = mockMovingBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);
        AgentGrindSearchStateRuntime.scheduleNextSearch(entry, 1_000L);
        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, constants.skills.Warrior.SLASH_BLAST, AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, AgentCombatSkillCacheStateRuntime.aoeSkillId(entry), 6);
        Monster target = mock(Monster.class);
        when(target.getPosition()).thenReturn(new Point(140, 100));
        AgentAttackPlan singleTargetPlan = basicClosePlan(target);

        // In range, but a multi-mob AoE bot stuck single-targeting should keep scanning for a cluster.
        assertTrue(AgentGrindTargetSearchPolicy.shouldSearchForGrindTarget(entry, bot, target, singleTargetPlan, 1_000L));
    }

    @Test
    void shouldAdoptSearchedTargetWhenNotCommitted() {
        MapleMap map = createEmptyTestMap(910000129);
        Character bot = mockMovingBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);
        Monster current = mock(Monster.class);
        when(current.getPosition()).thenReturn(new Point(300, 100)); // out of basic range
        Monster searched = mock(Monster.class);
        when(searched.getPosition()).thenReturn(new Point(160, 100));
        AgentAttackPlan plan = basicClosePlan(current);

        assertTrue(AgentGrindTargetSearchPolicy.shouldSwitchToSearchedTarget(entry, bot, current, searched, plan));
    }

    @Test
    void shouldNotSwitchInRangeTargetWhenClusterIsNotLarger() {
        MapleMap map = createEmptyTestMap(910000130);
        Character bot = mockMovingBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);
        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, constants.skills.Warrior.SLASH_BLAST, AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, AgentCombatSkillCacheStateRuntime.aoeSkillId(entry), 6);
        Monster current = mock(Monster.class);
        when(current.getPosition()).thenReturn(new Point(140, 100)); // in basic range
        Monster searched = mock(Monster.class);
        when(searched.getPosition()).thenReturn(new Point(160, 100));
        AgentAttackPlan plan = basicClosePlan(current);

        // Committed to an in-range target and the searched mob anchors no larger cluster (empty map).
        assertFalse(AgentGrindTargetSearchPolicy.shouldSwitchToSearchedTarget(entry, bot, current, searched, plan));
    }

    @Test
    void shouldReuseWanderDirectionWhenGrindHasNoTarget() {
        Character bot = mockMovingBot(new Point(100, 100), createEmptyTestMap(910000030));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);

        Point first = AgentGrindTargetRuntime.resolveNoGrindTargetPosition(entry, bot.getPosition());
        int direction = AgentGrindWanderStateRuntime.wanderDirection(entry);
        Point second = AgentGrindTargetRuntime.resolveNoGrindTargetPosition(entry, bot.getPosition());

        assertTrue(direction == -1 || direction == 1);
        assertEquals(new Point(100 + direction * 200, 100), first);
        assertEquals(first, second);
    }

    @Test
    void shouldIgnoreCachedGrindLootInsidePassiveLootRadiusWhenNoMobTarget() {
        Character bot = mockMovingBot(new Point(100, 100), createEmptyTestMap(910000034));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);
        AgentGrindWanderStateRuntime.setWanderDirection(entry, 1);
        MapItem nearbyLoot = mockLoot(1, new Point(100 + AgentRuntimeConfig.cfg.LOOT_RADIUS, 100));
        AgentGrindLootStateRuntime.setGrindLootTarget(entry, nearbyLoot);

        Point target = AgentGrindTargetRuntime.resolveNoGrindTargetPosition(entry, bot.getPosition());

        assertEquals(new Point(300, 100), target);
        assertNull(AgentGrindLootStateRuntime.grindLootTarget(entry));
    }

    @Test
    void shouldOnlyActivelySeekGrindLootOutsidePassiveLootRadius() {
        MapleMap map = spy(createEmptyTestMap(910000035));
        Character bot = mockMovingBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);
        MapItem passiveLoot = mockLoot(1, new Point(100 + AgentRuntimeConfig.cfg.LOOT_RADIUS, 100));
        MapItem activeLoot = mockLoot(2, new Point(100 + AgentRuntimeConfig.cfg.LOOT_RADIUS + 1, 100));
        int passiveLootObjectId = passiveLoot.getObjectId();
        int activeLootObjectId = activeLoot.getObjectId();
        doReturn(List.of(passiveLoot, activeLoot)).when(map).getDroppedItems();
        doReturn(passiveLoot).when(map).getMapObject(passiveLootObjectId);
        doReturn(activeLoot).when(map).getMapObject(activeLootObjectId);

        assertEquals(activeLoot, AgentLootTargetService.findNearestGrindLootTarget(
                entry, bot, AgentRuntimeConfig.cfg.LOOT_RADIUS, AgentGrindLootStateRuntime::isRetrySuppressed));
    }

    @Test
    void shouldNotActivelySeekFreshGrindBotInventoryDrop() {
        MapleMap map = spy(createEmptyTestMap(910000131));
        Character bot = mockMovingBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);
        MapItem loot = mockLoot(1, new Point(100 + AgentRuntimeConfig.cfg.LOOT_RADIUS + 1, 100));
        int lootObjectId = loot.getObjectId();
        Character dropBotOwner = mock(Character.class);
        Character dropBot = mock(Character.class);

        when(dropBot.getId()).thenReturn(99);
        when(loot.getOwnerId()).thenReturn(99);
        when(loot.isPlayerDrop()).thenReturn(true);
        when(loot.getDropTime()).thenReturn(System.currentTimeMillis() - 14_000L);
        doReturn(List.of(loot)).when(map).getDroppedItems();
        doReturn(loot).when(map).getMapObject(lootObjectId);

        AgentRuntimeRegistry.entriesByLeaderId().put(1, new CopyOnWriteArrayList<>(List.of(new AgentRuntimeEntry(dropBot, dropBotOwner, null))));
        try {

            assertNull(AgentLootTargetService.findNearestGrindLootTarget(
                    entry, bot, AgentRuntimeConfig.cfg.LOOT_RADIUS, AgentGrindLootStateRuntime::isRetrySuppressed));
        } finally {
            AgentRuntimeRegistry.entriesByLeaderId().clear();
        }
    }

    @Test
    void shouldClearFreshCachedGrindBotInventoryDrop() {
        MapleMap map = spy(createEmptyTestMap(910000132));
        Character bot = mockMovingBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);
        MapItem loot = mockLoot(1, new Point(100 + AgentRuntimeConfig.cfg.LOOT_RADIUS + 21, 100));
        int lootObjectId = loot.getObjectId();
        Character dropBotOwner = mock(Character.class);
        Character dropBot = mock(Character.class);

        AgentGrindLootStateRuntime.setGrindLootTarget(entry, loot);
        when(dropBot.getId()).thenReturn(99);
        when(loot.getOwnerId()).thenReturn(99);
        when(loot.isPlayerDrop()).thenReturn(true);
        when(loot.getDropTime()).thenReturn(System.currentTimeMillis() - 14_000L);
        doReturn(loot).when(map).getMapObject(lootObjectId);

        AgentRuntimeRegistry.entriesByLeaderId().put(1, new CopyOnWriteArrayList<>(List.of(new AgentRuntimeEntry(dropBot, dropBotOwner, null))));
        try {

            assertNull(AgentGrindTargetRuntime.convenientLootTarget(entry, bot.getPosition(), new Point(500, 100)));
            assertNull(AgentGrindLootStateRuntime.grindLootTarget(entry));
        } finally {
            AgentRuntimeRegistry.entriesByLeaderId().clear();
        }
    }

    @Test
    void shouldTemporarilyIgnoreGrindLootThatRemainsAfterEnteringPassiveLootRadius() {
        MapleMap map = spy(createEmptyTestMap(910000037));
        Character bot = mockMovingBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);
        MapItem loot = mockLoot(1, new Point(100 + AgentRuntimeConfig.cfg.LOOT_RADIUS, 100));
        int lootObjectId = loot.getObjectId();
        AgentGrindWanderStateRuntime.setWanderDirection(entry, 1);
        AgentGrindLootStateRuntime.setGrindLootTarget(entry, loot);
        doReturn(List.of(loot)).when(map).getDroppedItems();
        doReturn(loot).when(map).getMapObject(lootObjectId);

        AgentGrindTargetRuntime.resolveNoGrindTargetPosition(entry, bot.getPosition());
        bot.setPosition(new Point(99, 100));

        assertNull(AgentLootTargetService.findNearestGrindLootTarget(
                entry, bot, AgentRuntimeConfig.cfg.LOOT_RADIUS, AgentGrindLootStateRuntime::isRetrySuppressed));
    }

    @Test
    void shouldNotActivelySeekGrindLootWhenAnyInventoryIsFull() {
        MapleMap map = spy(createEmptyTestMap(910000038));
        Character bot = mockMovingBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);
        MapItem loot = mockLoot(1, new Point(100 + AgentRuntimeConfig.cfg.LOOT_RADIUS + 1, 100));
        Inventory fullEquip = mock(Inventory.class);
        when(fullEquip.isFull()).thenReturn(true);
        when(bot.getInventory(InventoryType.EQUIP)).thenReturn(fullEquip);
        doReturn(List.of(loot)).when(map).getDroppedItems();

        assertNull(AgentLootTargetService.findNearestGrindLootTarget(
                entry, bot, AgentRuntimeConfig.cfg.LOOT_RADIUS, AgentGrindLootStateRuntime::isRetrySuppressed));
    }

    @Test
    void shouldNotActivelySeekKpqPassDrops() {
        MapleMap map = spy(createEmptyTestMap(910000039));
        Character bot = mockMovingBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);
        MapItem pass = mockLoot(1, new Point(100 + AgentRuntimeConfig.cfg.LOOT_RADIUS + 1, 100), 4001008, 0, 0);
        int passObjectId = pass.getObjectId();
        doReturn(List.of(pass)).when(map).getDroppedItems();
        doReturn(pass).when(map).getMapObject(passObjectId);

        assertNull(AgentLootTargetService.findNearestGrindLootTarget(
                entry, bot, AgentRuntimeConfig.cfg.LOOT_RADIUS, AgentGrindLootStateRuntime::isRetrySuppressed));
    }

    @Test
    void shouldNotActivelySeekSkippedKpqCouponDrops() {
        MapleMap map = spy(createEmptyTestMap(910000040));
        Character bot = mockMovingBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);
        AgentPqRuntime.setKpqStageState(entry, 4); // KPQ stage 1 SECOND_WALK: coupons should no longer be looted.
        MapItem coupon = mockLoot(1, new Point(100 + AgentRuntimeConfig.cfg.LOOT_RADIUS + 1, 100), 4001007, 0, 0);
        int couponObjectId = coupon.getObjectId();
        doReturn(List.of(coupon)).when(map).getDroppedItems();
        doReturn(coupon).when(map).getMapObject(couponObjectId);

        assertNull(AgentLootTargetService.findNearestGrindLootTarget(
                entry, bot, AgentRuntimeConfig.cfg.LOOT_RADIUS, AgentGrindLootStateRuntime::isRetrySuppressed));
    }

    @Test
    void shouldNotActivelySeekUnneededQuestDrops() {
        MapleMap map = spy(createEmptyTestMap(910000041));
        Character bot = mockMovingBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);
        MapItem questDrop = mockLoot(1, new Point(100 + AgentRuntimeConfig.cfg.LOOT_RADIUS + 1, 100), 4000000, 0, 2000);
        int questDropObjectId = questDrop.getObjectId();
        when(bot.needQuestItem(2000, 4000000)).thenReturn(false);
        doReturn(List.of(questDrop)).when(map).getDroppedItems();
        doReturn(questDrop).when(map).getMapObject(questDropObjectId);

        assertNull(AgentLootTargetService.findNearestGrindLootTarget(
                entry, bot, AgentRuntimeConfig.cfg.LOOT_RADIUS, AgentGrindLootStateRuntime::isRetrySuppressed));
    }

    @Test
    void shouldScoreGrindLootByTravelNeededBeyondPassiveLootRadius() {
        MapleMap map = spy(createEmptyTestMap(910000036));
        Character bot = mockMovingBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, mock(Character.class), null);
        Point botPos = bot.getPosition();
        Point mobPos = new Point(500, 100);
        Point lootPos = new Point(100 + AgentRuntimeConfig.cfg.LOOT_RADIUS + 21, 100);
        MapItem loot = mockLoot(1, lootPos);
        int lootObjectId = loot.getObjectId();
        AgentGrindLootStateRuntime.setGrindLootTarget(entry, loot);
        doReturn(loot).when(map).getMapObject(lootObjectId);

        assertEquals(lootPos, AgentGrindTargetRuntime.convenientLootTarget(entry, botPos, mobPos));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldResolveFollowTargetRegionFromFollowAnchorInsteadOfOwner() throws Exception {
        MapleMap map = createEmptyTestMap(910000025);
        map.getFootholds().insert(new Foothold(new Point(0, 100), new Point(400, 100), 1));
        map.addRope(new Rope(100, 40, 100, false));
        AgentNavigationGraphService.rebuildGraph(map);

        Character owner = mock(Character.class);
        when(owner.getId()).thenReturn(77);
        when(owner.getMap()).thenReturn(map);
        when(owner.getPosition()).thenReturn(new Point(100, 60));
        when(owner.getStance()).thenReturn(CharacterStance.LADDER_STANCE);

        Character follower = mockMovingBot(new Point(100, 60), map);
        when(follower.getId()).thenReturn(88);
        Character followAnchor = mockMovingBot(new Point(300, 100), map);
        when(followAnchor.getId()).thenReturn(99);
        when(followAnchor.getName()).thenReturn("BotB");
        when(followAnchor.isLoggedinWorld()).thenReturn(true);

        AgentRuntimeEntry followerEntry = new AgentRuntimeEntry(follower, owner, null);
        AgentModeStateRuntime.setFollowing(followerEntry, true);
        AgentModeStateRuntime.setFollowTargetId(followerEntry, followAnchor.getId());
        AgentRuntimeEntry anchorEntry = new AgentRuntimeEntry(followAnchor, owner, null);

        Map<Integer, List<AgentRuntimeEntry>> bots = AgentRuntimeRegistry.entriesByLeaderId();
        bots.put(owner.getId(), List.of(followerEntry, anchorEntry));
        try {
            AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(map);
            int targetRegionId = AgentNavigationRegionService.resolveTargetRegionId(
                    graph, followerEntry, map, new Point(300, 100));
            AgentNavigationGraph.Region targetRegion = graph.getRegion(targetRegionId);

            assertNotNull(targetRegion);
            assertFalse(targetRegion.isRopeRegion,
                    "botA follow botB should resolve navigation against botB, not owner's rope");
            assertEquals("BotB", AgentTargetSnapshotRuntime.captureTargetSnapshot(followerEntry).followAnchorName());
        } finally {
            bots.remove(owner.getId());
        }
    }

    @Test
    void shouldUseShopTargetAsPrimaryWhileResupplying() {
        MapleMap map = createEmptyTestMap(910000026);
        Character owner = mockMovingBot(new Point(50, 100), map);
        Character bot = mockMovingBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentShopStateRuntime.startShopVisit(entry, new Point(900, 100), new Point(850, 100), 0, 1_000L);

        AgentTargetSnapshot snapshot = AgentTargetSnapshotRuntime.captureTargetSnapshot(entry);

        assertEquals(new Point(850, 100), snapshot.primaryTargetPos());
        assertEquals("shop-target", snapshot.primaryTargetSource());
    }

    @Test
    void shouldUseFarmHereAnchorAsPrimaryTargetAndEnterGrindMode() {
        MapleMap map = createEmptyTestMap(910000031);
        Character owner = mockMovingBot(new Point(50, 100), map);
        Character bot = mockMovingBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);

        AgentMovementCommandRuntime.farmHere(entry, new Point(300, 100));

        assertEquals(new Point(300, 100), AgentFarmAnchorStateRuntime.farmAnchor(entry));
        assertEquals(map.getId(), AgentFarmAnchorStateRuntime.farmAnchorMapId(entry));
        assertEquals(new Point(300, 100), AgentMoveTargetStateRuntime.moveTarget(entry));
        assertTrue(AgentMoveTargetStateRuntime.isPrecise(entry));
        assertFalse(AgentModeStateRuntime.following(entry));
        assertTrue(AgentModeStateRuntime.grinding(entry));

        AgentTargetSnapshot snapshot = AgentTargetSnapshotRuntime.captureTargetSnapshot(entry);
        assertEquals(new Point(300, 100), snapshot.primaryTargetPos());
        assertEquals("move-target", snapshot.primaryTargetSource());
    }

    @Test
    void shouldKeepFarmHereAnchorPrimaryAfterArrivalClearsMoveTarget() {
        MapleMap map = createEmptyTestMap(910000032);
        Character owner = mockMovingBot(new Point(50, 100), map);
        Character bot = mockMovingBot(new Point(300, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);
        AgentFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(300, 100), map.getId());

        AgentTargetSnapshot snapshot = AgentTargetSnapshotRuntime.captureTargetSnapshot(entry);

        assertEquals(new Point(300, 100), snapshot.primaryTargetPos());
        assertEquals("farm-anchor", snapshot.primaryTargetSource());
    }

    @Test
    void shouldCancelShopVisitWhenOwnerIssuesFollow() {
        MapleMap map = createEmptyTestMap(910000027);
        Character owner = mockMovingBot(new Point(50, 100), map);
        Character bot = mockMovingBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);
        AgentShopStateRuntime.startShopVisit(entry, new Point(900, 100), new Point(850, 100), 0, 1_000L);
        AgentShopStateRuntime.markShopSequenceActive(entry, 2_000L);

        AgentMovementCommandRuntime.followOwner(entry);

        assertFalse(AgentShopStateRuntime.shopVisitPending(entry));
        assertFalse(AgentShopStateRuntime.shopSequenceActive(entry));
        assertNull(AgentShopStateRuntime.shopNpcPosition(entry));
        assertNull(AgentShopStateRuntime.shopTargetPosition(entry));
        assertTrue(AgentModeStateRuntime.following(entry));
    }

    @Test
    void shouldClearFarmHereAnchorWhenOwnerIssuesFollow() {
        MapleMap map = createEmptyTestMap(910000033);
        Character owner = mockMovingBot(new Point(50, 100), map);
        Character bot = mockMovingBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);
        AgentFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(300, 100), map.getId());
        AgentMoveTargetStateRuntime.setPreciseMoveTarget(entry, new Point(300, 100));

        AgentMovementCommandRuntime.followOwner(entry);

        assertNull(AgentFarmAnchorStateRuntime.farmAnchor(entry));
        assertEquals(-1, AgentFarmAnchorStateRuntime.farmAnchorMapId(entry));
        assertNull(AgentMoveTargetStateRuntime.moveTarget(entry));
        assertFalse(AgentMoveTargetStateRuntime.isPrecise(entry));
        assertTrue(AgentModeStateRuntime.following(entry));
    }

    @Test
    void shouldCancelShopVisitWhenOwnerIssuesStop() {
        MapleMap map = createEmptyTestMap(910000034);
        Character owner = mockMovingBot(new Point(50, 100), map);
        Character bot = mockMovingBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);
        AgentShopStateRuntime.startShopVisit(entry, new Point(900, 100), new Point(850, 100), 0, 1_000L);
        AgentShopStateRuntime.markShopSequenceActive(entry, 2_000L);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentMoveTargetStateRuntime.setPreciseMoveTarget(entry, new Point(300, 100));

        AgentMovementCommandRuntime.stop(entry);

        assertFalse(AgentShopStateRuntime.shopVisitPending(entry));
        assertFalse(AgentShopStateRuntime.shopSequenceActive(entry));
        assertNull(AgentShopStateRuntime.shopNpcPosition(entry));
        assertNull(AgentShopStateRuntime.shopTargetPosition(entry));
        assertFalse(AgentModeStateRuntime.following(entry));
        assertNull(AgentMoveTargetStateRuntime.moveTarget(entry));
    }

    @Test
    void shouldEnterGrindThroughAgentMovementCommandRuntime() {
        MapleMap map = createEmptyTestMap(910000035);
        Character owner = mockMovingBot(new Point(50, 100), map);
        Character bot = mockMovingBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentMoveTargetStateRuntime.setPreciseMoveTarget(entry, new Point(300, 100));

        AgentMovementCommandRuntime.grind(entry);

        assertFalse(AgentModeStateRuntime.following(entry));
        assertTrue(AgentModeStateRuntime.grinding(entry));
        assertNull(AgentMoveTargetStateRuntime.moveTarget(entry));
    }

    @Test
    void shouldKeepTenMinutePotShareBackoffSeparateForHpAndMp() throws Exception {
        MapleMap map = mock(MapleMap.class);
        Character owner = mock(Character.class);
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);

        when(owner.getId()).thenReturn(77);
        when(owner.getName()).thenReturn("Owner");
        when(bot.getId()).thenReturn(88);
        when(bot.getTrade()).thenReturn(null);
        when(bot.getMap()).thenReturn(map);

        @SuppressWarnings("unchecked")
        Map<Integer, List<AgentRuntimeEntry>> bots = AgentRuntimeRegistry.entriesByLeaderId();
        @SuppressWarnings("unchecked")
        Map<Integer, Long> sharedCooldown = (Map<Integer, Long>) field(AgentPotionService.class, "potShareCooldownUntil").get(null);
        @SuppressWarnings("unchecked")
        Map<Integer, Long> hpBackoff = (Map<Integer, Long>) field(AgentPotionService.class, "potShareHpBackoffUntil").get(null);
        @SuppressWarnings("unchecked")
        Map<Integer, Long> mpBackoff = (Map<Integer, Long>) field(AgentPotionService.class, "potShareMpBackoffUntil").get(null);

        bots.put(owner.getId(), List.of(entry));
        sharedCooldown.remove(owner.getId());
        hpBackoff.remove(owner.getId());
        mpBackoff.remove(owner.getId());

        Method requestPotShare = AgentPotionService.class.getDeclaredMethod("requestPotShare", AgentRuntimeEntry.class, Character.class, boolean.class);
        requestPotShare.setAccessible(true);
        try {
            assertTrue((Boolean) requestPotShare.invoke(null, entry, bot, false),
                    "first MP request should broadcast and install MP-only long backoff when no donor exists");
            assertTrue(mpBackoff.get(owner.getId()) > System.currentTimeMillis());
            assertFalse(hpBackoff.containsKey(owner.getId()));

            sharedCooldown.put(owner.getId(), 0L);

            assertTrue((Boolean) requestPotShare.invoke(null, entry, bot, true),
                    "HP request should still be allowed after shared 30 s cooldown even if MP is under 10 min backoff");
            assertTrue(hpBackoff.get(owner.getId()) > System.currentTimeMillis());

            sharedCooldown.put(owner.getId(), 0L);
            assertFalse((Boolean) requestPotShare.invoke(null, entry, bot, false),
                    "MP request should remain blocked by its own 10 min backoff");
        } finally {
            bots.remove(owner.getId());
            sharedCooldown.remove(owner.getId());
            hpBackoff.remove(owner.getId());
            mpBackoff.remove(owner.getId());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldLetOwnerPotRequestsBypassShareCooldowns() throws Exception {
        Character owner = mock(Character.class);
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);

        when(owner.getId()).thenReturn(79);
        when(owner.getTrade()).thenReturn(null);

        Map<Integer, List<AgentRuntimeEntry>> bots = AgentRuntimeRegistry.entriesByLeaderId();
        Map<Integer, Long> sharedCooldown = (Map<Integer, Long>) field(AgentPotionService.class, "potShareCooldownUntil").get(null);
        Map<Integer, Long> hpBackoff = (Map<Integer, Long>) field(AgentPotionService.class, "potShareHpBackoffUntil").get(null);

        bots.put(owner.getId(), List.of());
        sharedCooldown.put(owner.getId(), Long.MAX_VALUE);
        hpBackoff.put(owner.getId(), Long.MAX_VALUE);
        try {
            assertEquals(AgentPotionService.OwnerPotShareResult.NO_DONOR,
                    AgentPotionService.offerPotShareToOwner(entry, true),
                    "manual owner requests should still attempt donor lookup while automatic share cooldowns are active");
        } finally {
            bots.remove(owner.getId());
            sharedCooldown.remove(owner.getId());
            hpBackoff.remove(owner.getId());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldLetOwnerAmmoRequestsBypassShareCooldowns() throws Exception {
        Character owner = mock(Character.class);
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);

        when(owner.getId()).thenReturn(80);
        when(owner.getMapId()).thenReturn(1000);
        when(owner.getTrade()).thenReturn(null);

        Map<Integer, List<AgentRuntimeEntry>> bots = AgentRuntimeRegistry.entriesByLeaderId();
        Map<Integer, Long> sharedCooldown = (Map<Integer, Long>) field(AgentAmmoService.class, "ammoShareCooldownUntil").get(null);
        Map<String, Long> backoff = (Map<String, Long>) field(AgentAmmoService.class, "ammoShareBackoffUntil").get(null);
        String backoffKey = owner.getId() + ":" + WeaponType.BOW.name();

        bots.put(owner.getId(), List.of());
        sharedCooldown.put(owner.getId(), Long.MAX_VALUE);
        backoff.put(backoffKey, Long.MAX_VALUE);
        try {
            assertEquals(AgentAmmoService.OwnerAmmoShareResult.NO_DONOR,
                    AgentAmmoService.offerAmmoShareToOwner(entry, WeaponType.BOW),
                    "manual owner ammo requests should still attempt donor lookup while automatic share cooldowns are active");
        } finally {
            bots.remove(owner.getId());
            sharedCooldown.remove(owner.getId());
            backoff.remove(backoffKey);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldLetPlayerAskedBotPotRequestBypassShareCooldowns() throws Exception {
        MapleMap map = mock(MapleMap.class);
        Character owner = mock(Character.class);
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);

        when(owner.getId()).thenReturn(81);
        when(owner.getName()).thenReturn("Owner");
        when(bot.getId()).thenReturn(82);
        when(bot.getTrade()).thenReturn(null);
        when(bot.getMap()).thenReturn(map);

        Map<Integer, List<AgentRuntimeEntry>> bots = AgentRuntimeRegistry.entriesByLeaderId();
        Map<Integer, Long> sharedCooldown = (Map<Integer, Long>) field(AgentPotionService.class, "potShareCooldownUntil").get(null);
        Map<Integer, Long> hpBackoff = (Map<Integer, Long>) field(AgentPotionService.class, "potShareHpBackoffUntil").get(null);

        bots.put(owner.getId(), List.of(entry));
        sharedCooldown.put(owner.getId(), Long.MAX_VALUE);
        hpBackoff.put(owner.getId(), Long.MAX_VALUE);

        try {
            assertTrue(AgentPotionService.requestPotShare(entry, bot, true, true),
                    "player-asked bot supply checks should bypass automatic cooldown/backoff guards");
            assertEquals(Long.MAX_VALUE, sharedCooldown.get(owner.getId()));
            assertEquals(Long.MAX_VALUE, hpBackoff.get(owner.getId()));
        } finally {
            bots.remove(owner.getId());
            sharedCooldown.remove(owner.getId());
            hpBackoff.remove(owner.getId());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldLetPlayerAskedBotAmmoRequestBypassShareCooldowns() throws Exception {
        MapleMap map = mock(MapleMap.class);
        Character owner = mock(Character.class);
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);

        when(owner.getId()).thenReturn(83);
        when(bot.getTrade()).thenReturn(null);
        when(bot.getMap()).thenReturn(map);

        Map<Integer, List<AgentRuntimeEntry>> bots = AgentRuntimeRegistry.entriesByLeaderId();
        Map<Integer, Long> sharedCooldown = (Map<Integer, Long>) field(AgentAmmoService.class, "ammoShareCooldownUntil").get(null);
        Map<String, Long> backoff = (Map<String, Long>) field(AgentAmmoService.class, "ammoShareBackoffUntil").get(null);
        String backoffKey = owner.getId() + ":" + WeaponType.BOW.name();

        bots.put(owner.getId(), List.of(entry));
        sharedCooldown.put(owner.getId(), Long.MAX_VALUE);
        backoff.put(backoffKey, Long.MAX_VALUE);

        try {
            assertTrue(AgentAmmoService.requestAmmoShare(entry, bot, WeaponType.BOW, 0, true),
                    "player-asked bot supply checks should bypass automatic cooldown/backoff guards");
            assertEquals(Long.MAX_VALUE, sharedCooldown.get(owner.getId()));
            assertEquals(Long.MAX_VALUE, backoff.get(backoffKey));
        } finally {
            bots.remove(owner.getId());
            sharedCooldown.remove(owner.getId());
            backoff.remove(backoffKey);
        }
    }

    @Test
    void shouldPreferNonAmmoUsersWhenSharingArrows() throws Exception {
        Character owner = mock(Character.class);
        Character needy = ammoBot(10, 1000, 100);
        Character nonBow800 = ammoBot(11, 1000, 800);
        Character nonBow600 = ammoBot(12, 1000, 600);
        Character bow3000 = ammoBot(13, 1000, 3000);
        Character ignored499 = ammoBot(14, 1000, 499);

        when(owner.getId()).thenReturn(77);

        AgentRuntimeEntry needyEntry = new AgentRuntimeEntry(needy, owner, null);
        AgentRuntimeEntry nonBow800Entry = new AgentRuntimeEntry(nonBow800, owner, null);
        AgentRuntimeEntry nonBow600Entry = new AgentRuntimeEntry(nonBow600, owner, null);
        AgentRuntimeEntry bow3000Entry = new AgentRuntimeEntry(bow3000, owner, null);
        AgentRuntimeEntry ignored499Entry = new AgentRuntimeEntry(ignored499, owner, null);

        @SuppressWarnings("unchecked")
        Map<Integer, List<AgentRuntimeEntry>> bots = AgentRuntimeRegistry.entriesByLeaderId();
        bots.put(owner.getId(), List.of(needyEntry, nonBow600Entry, bow3000Entry, ignored499Entry, nonBow800Entry));

        try (MockedStatic<AgentAttackExecutionProvider> attacks = mockStatic(AgentAttackExecutionProvider.class, invocation -> {
            Character character = invocation.getArgument(0);
            if (character == needy || character == bow3000) {
                return WeaponType.BOW;
            }
            return WeaponType.SWORD1H;
        })) {

            AgentAmmoDonorPlan<AgentRuntimeEntry> plan = AgentAmmoService.selectAmmoDonor(needyEntry, needy, WeaponType.BOW);

            assertNotNull(plan);
            assertEquals(nonBow800Entry, plan.entry());
            assertEquals(800, plan.donationQty());
            assertFalse(plan.donorNeedsSameAmmo());
        } finally {
            bots.remove(owner.getId());
        }
    }

    @Test
    void shouldOnlyDonateHalfSurplusFromSameAmmoUser() throws Exception {
        Character owner = mock(Character.class);
        Character needy = ammoBot(10, 1000, 100);
        Character bow3000 = ammoBot(13, 1000, 3000);

        when(owner.getId()).thenReturn(78);

        AgentRuntimeEntry needyEntry = new AgentRuntimeEntry(needy, owner, null);
        AgentRuntimeEntry bow3000Entry = new AgentRuntimeEntry(bow3000, owner, null);

        @SuppressWarnings("unchecked")
        Map<Integer, List<AgentRuntimeEntry>> bots = AgentRuntimeRegistry.entriesByLeaderId();
        bots.put(owner.getId(), List.of(needyEntry, bow3000Entry));

        try (MockedStatic<AgentAttackExecutionProvider> attacks = mockStatic(AgentAttackExecutionProvider.class,
                invocation -> WeaponType.BOW)) {
            AgentAmmoDonorPlan<AgentRuntimeEntry> plan = AgentAmmoService.selectAmmoDonor(needyEntry, needy, WeaponType.BOW);

            assertNotNull(plan);
            assertEquals(bow3000Entry, plan.entry());
            assertTrue(plan.donorNeedsSameAmmo());
            assertEquals(1250, plan.donationQty());
        } finally {
            bots.remove(owner.getId());
        }
    }

    @Test
    void shouldSplitSingleAmmoStackByShareBudget() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AgentPendingTradeStateRuntime.setShareBudget(entry, 2250);

        short tradeQty = AgentPendingTradeStateRuntime.capShareQuantity(entry, (short) 5000);

        assertEquals(2250, tradeQty);
        assertEquals(0, AgentPendingTradeStateRuntime.shareBudget(entry));
    }

    @Test
    void shouldRestoreTradeWindowCopyAfterTemporarilyUnequippedItemIsAddedToTrade() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Item equippedItem = new Item(1040000, (short) 1, (short) 1);
        Item tradeWindowCopy = equippedItem.copy();

        AgentPendingTradeStateRuntime.rememberRestoreSlot(entry, equippedItem, (short) -5);

        AgentPendingTradeStateRuntime.transferRestoreSlot(entry, equippedItem, tradeWindowCopy);

        assertFalse(AgentPendingTradeStateRuntime.restoreSlotEntries(entry).stream()
                .anyMatch(restore -> restore.getKey() == equippedItem));
        assertEquals((short) -5, AgentPendingTradeStateRuntime.restoreSlotEntries(entry).stream()
                .filter(restore -> restore.getKey() == tradeWindowCopy)
                .findFirst()
                .orElseThrow()
                .getValue());
    }

    @Test
    void shouldPrioritizeEtcTradeItemsRecipientAlreadyHasBeforeItemIdOrder() {
        Character recipient = mock(Character.class);
        Inventory etcInventory = new Inventory(recipient, InventoryType.ETC, (byte) 24);

        etcInventory.addItem(new Item(4000001, (short) 1, (short) 20));
        when(recipient.getInventory(InventoryType.ETC)).thenReturn(etcInventory);

        Item item4000002 = new Item(4000002, (short) 3, (short) 10);
        Item item4000000 = new Item(4000000, (short) 1, (short) 10);
        Item item4000001 = new Item(4000001, (short) 2, (short) 10);

        List<Item> ordered = AgentInventoryTradePolicy.prioritizeEtcTradeItems(
                List.of(item4000002, item4000000, item4000001), recipient);

        assertEquals(List.of(item4000001, item4000000, item4000002), ordered);
    }

    @Test
    void shouldMatchNaturalSupplyRequestPhrases() {
        assertTrue(AgentChatCommandClassifier.isNeedPotCommand("nned pot"));
        assertTrue(AgentChatCommandClassifier.isNeedPotCommand("need some pots"));
        assertTrue(AgentChatCommandClassifier.isNeedPotCommand("anybody got pot"));
        assertTrue(AgentChatCommandClassifier.isNeedPotCommand("low on pots"));
        assertTrue(AgentChatCommandClassifier.isNeedHpPotCommand("anyone have hp pots"));
        assertTrue(AgentChatCommandClassifier.isNeedMpPotCommand("running low on mana potions"));
        assertTrue(AgentChatCommandClassifier.isNeedAmmoCommand("anybody got arrows"));
        assertTrue(AgentChatCommandClassifier.isNeedAmmoCommand("low on ammo"));
    }

    @Test
    void shouldNormalizeNamedItemCommandsAndQueries() {
        assertEquals("warrior potion", AgentItemQueryNormalizer.normalize("Warrior Potions?!"));
        assertEquals("name:warrior potion", AgentTradeDialogueClassifier.matchChoiceCategory("drop warrior potions?"));
        assertEquals("name:warrior potion", AgentTradeDialogueClassifier.matchTradeCategory("trade me warrior potions"));
        assertEquals("warrior potion", AgentTradeDialogueClassifier.matchItemQuery("anybody got warrior potions?"));
    }

    @Test
    void shouldPrioritizeRecipientDuplicatesWithinUseTradeBuckets() {
        Character owner = mock(Character.class);
        Inventory ownerUse = new Inventory(owner, InventoryType.USE, (byte) 24);
        ownerUse.addItem(Items.itemWithQuantity(2030000, 1));
        ownerUse.addItem(Items.itemWithQuantity(2040000, 1));
        when(owner.getInventory(InventoryType.USE)).thenReturn(ownerUse);

        List<Item> ordered = AgentInventoryTradePolicy.prioritizeTradeUseItems(
                List.of(
                        Items.itemWithQuantity(2030001, 1),
                        Items.itemWithQuantity(2030000, 1)),
                List.of(
                        Items.itemWithQuantity(2040000, 1)),
                List.of(
                        Items.itemWithQuantity(2060000, 1)),
                owner);

        assertEquals(List.of(2030000, 2030001, 2040000, 2060000),
                ordered.stream().map(Item::getItemId).toList());
    }

    @Test
    void shouldRankPotionAndAmmoLastWithinUseTradeBuckets() {
        Character owner = mock(Character.class);
        Inventory ownerUse = new Inventory(owner, InventoryType.USE, (byte) 24);
        ownerUse.addItem(Items.itemWithQuantity(2000000, 1)); // potion owner already has
        ownerUse.addItem(Items.itemWithQuantity(2040000, 1)); // scroll owner already has
        when(owner.getInventory(InventoryType.USE)).thenReturn(ownerUse);

        List<Item> ordered = AgentInventoryTradePolicy.prioritizeTradeUseItems(
                List.of(Items.itemWithQuantity(2030000, 1)),
                List.of(
                        Items.itemWithQuantity(2040001, 1),
                        Items.itemWithQuantity(2040000, 1)),
                List.of(
                        Items.itemWithQuantity(2000001, 1),
                        Items.itemWithQuantity(2000000, 1)),
                owner);

        assertEquals(List.of(2030000, 2040000, 2040001, 2000000, 2000001),
                ordered.stream().map(Item::getItemId).toList());
    }

    @Test
    void shouldPrioritizeScrollTradeItemsRecipientAlreadyHasBeforeItemIdOrder() {
        Character recipient = mock(Character.class);
        Inventory recipientUse = new Inventory(recipient, InventoryType.USE, (byte) 24);
        recipientUse.addItem(Items.itemWithQuantity(2040001, 1));
        when(recipient.getInventory(InventoryType.USE)).thenReturn(recipientUse);

        List<Item> ordered = AgentInventoryTradePolicy.prioritizeScrollTradeItems(
                List.of(
                        Items.itemWithQuantity(2040002, 1),
                        Items.itemWithQuantity(2040000, 1),
                        Items.itemWithQuantity(2040001, 1)),
                recipient);

        assertEquals(List.of(2040001, 2040000, 2040002),
                ordered.stream().map(Item::getItemId).toList());
    }

    private static AgentAttackPlan basicClosePlan(Monster target) {
        return new AgentAttackPlan(
                0, 0, 1, null, List.of(target), AgentAttackRoute.CLOSE,
                0, 0, 0, 0, 0, 0, 0, null);
    }

    private static AgentGrindNavigationTargetSelector.NavigationHooks grindNavigationHooks() {
        return new AgentGrindNavigationTargetSelector.NavigationHooks(
                AgentNavigationRegionService::resolveCurrentRegionId,
                AgentNavigationRegionService::resolveTargetRegionId,
                AgentNavigationPathService::findPath,
                AgentMovementPhysicsConfig.configuredGrindEdgeMargin(),
                AgentMovementPhysicsConfig.configuredJumpYThreshold());
    }

    private static MapleMap createEmptyTestMap(int mapId) {
        MapleMap map = new MapleMap(mapId, 0, 0, mapId, 1.0f);
        map.setFootholds(new FootholdTree(new Point(-2000, -2000), new Point(2000, 2000)));
        return map;
    }

    private static Character mockMovingBot(Point startPosition, MapleMap map) {
        Character bot = mock(Character.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(startPosition));
        AtomicInteger stance = new AtomicInteger(0);
        int mapId = map.getId();

        when(bot.getId()).thenReturn(88);
        when(bot.getMap()).thenReturn(map);
        when(bot.getMapId()).thenReturn(mapId);
        when(bot.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(bot).setPosition(any(Point.class));
        when(bot.getHp()).thenReturn(100);
        when(bot.getTotalMoveSpeedStat()).thenReturn(100);
        when(bot.getTotalJumpStat()).thenReturn(100);
        when(bot.getStance()).thenAnswer(invocation -> stance.get());
        doAnswer(invocation -> {
            stance.set(invocation.getArgument(0));
            return null;
        }).when(bot).setStance(anyInt());
        return bot;
    }

    private static AgentRuntimeEntry botEntryNamed(String name) {
        Character bot = mock(Character.class);
        when(bot.getName()).thenReturn(name);
        return new AgentRuntimeEntry(bot, null, null);
    }

    private static Monster mockMob(Point position, int id) {
        Monster mob = mock(Monster.class);
        when(mob.getPosition()).thenReturn(new Point(position));
        when(mob.getId()).thenReturn(id);
        when(mob.getObjectId()).thenReturn(id);
        when(mob.isAlive()).thenReturn(true);
        return mob;
    }

    private static MapItem mockLoot(int objectId, Point position) {
        return mockLoot(objectId, position, 0, 1, 0);
    }

    private static MapItem mockLoot(int objectId, Point position, int itemId, int meso, int questId) {
        MapItem loot = mock(MapItem.class);
        when(loot.getObjectId()).thenReturn(objectId);
        when(loot.getPosition()).thenReturn(new Point(position));
        when(loot.isPickedUp()).thenReturn(false);
        when(loot.canBePickedBy(any(Character.class))).thenReturn(true);
        when(loot.getDropTime()).thenReturn(System.currentTimeMillis() - 5_000L);
        when(loot.getItemId()).thenReturn(itemId);
        when(loot.getMeso()).thenReturn(meso);
        when(loot.getQuest()).thenReturn(questId);
        return loot;
    }

    private static Character ammoBot(int id, int mapId, int arrowCount) {
        Character bot = mock(Character.class);
        Inventory use = new Inventory(bot, InventoryType.USE, (byte) 24);
        use.addItem(Items.itemWithQuantity(2060000, arrowCount));
        when(bot.getId()).thenReturn(id);
        when(bot.getMapId()).thenReturn(mapId);
        when(bot.getInventory(InventoryType.USE)).thenReturn(use);
        when(bot.getBuffedValue(any(BuffStat.class))).thenReturn(null);
        return bot;
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


