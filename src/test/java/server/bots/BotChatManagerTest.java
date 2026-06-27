package server.bots;


import server.agents.integration.AgentBotReplyRuntime;
import client.Character;
import client.Job;
import client.inventory.Inventory;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotChatReportRuntime;
import server.agents.integration.AgentBotActivityStateRuntime;
import server.agents.integration.AgentBotMessageQueueStateRuntime;
import server.agents.integration.AgentBotChatStatusRuntime;
import server.agents.integration.AgentBotOfferStateRuntime;
import server.agents.integration.AgentBotPendingActionStateRuntime;
import server.agents.capabilities.dialogue.AgentTradeDialogueClassifier;
import server.agents.capabilities.dialogue.AgentChatCommandClassifier;
import server.agents.commands.AgentQueuedMessage;
import server.maps.FieldLimit;
import server.maps.MapleMap;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotChatManagerTest {
    @Test
    void shouldParseTradeMesosAsAllWhenNoAmountIsSpecified() {
        assertEquals("mesos", AgentTradeDialogueClassifier.matchTradeCategory("trade mesos"));
        assertEquals("mesos", AgentTradeDialogueClassifier.matchTradeCategory("trade me all your mesos"));
    }

    @Test
    void shouldParseTradeMesosWithExplicitAmounts() {
        assertEquals("mesos:1000000", AgentTradeDialogueClassifier.matchTradeCategory("trade 1m mesos"));
        assertEquals("mesos:1250000", AgentTradeDialogueClassifier.matchTradeCategory("trade 1,250,000 mesos"));
        assertEquals("mesos:1500000", AgentTradeDialogueClassifier.matchTradeCategory("trade 1.5m mesos"));
    }

    @Test
    void shouldParseAdditionalMesoTransferPhrasings() {
        assertEquals("mesos:5000000", AgentTradeDialogueClassifier.matchTradeCategory("give me 5m"));
        assertEquals("mesos:200000", AgentTradeDialogueClassifier.matchTradeCategory("gimme 200000"));
        assertEquals("mesos", AgentTradeDialogueClassifier.matchTradeCategory("trade meso"));
        assertEquals("mesos:10000000", AgentTradeDialogueClassifier.matchTradeCategory("give meso 10m"));
        assertEquals("mesos:10000000", AgentTradeDialogueClassifier.matchTradeCategory("trade 10m"));
    }

    @Test
    void shouldStillParseNamedItemTrades() {
        assertEquals("name:chaos scroll", AgentTradeDialogueClassifier.matchTradeCategory("trade chaos scroll"));
        assertEquals("name:chaos scroll", AgentTradeDialogueClassifier.matchTradeCategory("trade chaos scrolls"));
    }

    @Test
    void shouldParseViewEquipmentRequestsAsTradeCommands() {
        assertEquals("name:hat", AgentTradeDialogueClassifier.matchTradeCategory("show me your hat"));
        assertEquals("name:ring 2", AgentTradeDialogueClassifier.matchTradeCategory("let me see ur ring 2"));
        assertEquals("name:weapon", AgentTradeDialogueClassifier.matchTradeCategory("can i c yo weapon"));
    }

    @Test
    void shouldParseFollowTargetCommandsWithoutBreakingPlainFollow() {
        assertEquals("clawer", AgentChatCommandClassifier.matchFollowTarget("follow clawer"));
        assertEquals("Clawer", AgentChatCommandClassifier.matchFollowTarget("follow Clawer please"));
        assertNull(AgentChatCommandClassifier.matchFollowTarget("follow me"));
        assertNull(AgentChatCommandClassifier.matchFollowTarget("follow here"));
    }

    @Test
    void shouldOnlyMatchMovementModeCommandsAsWholeCommands() {
        assertTrue(AgentChatCommandClassifier.isMoveHereCommand("here"));
        assertTrue(AgentChatCommandClassifier.isMoveHereCommand("move here!"));
        assertFalse(AgentChatCommandClassifier.isMoveHereCommand("some random chat message here"));

        assertTrue(AgentChatCommandClassifier.isGrindCommand("farm"));
        assertTrue(AgentChatCommandClassifier.isGrindCommand("go grind"));
        assertFalse(AgentChatCommandClassifier.isGrindCommand("Im going to the farm today"));

        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("farm here"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("grind here please"));
        assertFalse(AgentChatCommandClassifier.isFarmHereCommand("Im going to farm here today"));

        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("sentry"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("go sentry"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("sentry here"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("sentry mode"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("go sentry mode"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("camp"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("camp here"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("guard mode"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("go defend mode"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("post up"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("post up here"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("anchor here"));
        assertTrue(AgentChatCommandClassifier.isFarmHereCommand("anchor"));
        assertFalse(AgentChatCommandClassifier.isFarmHereCommand("Im going to camp today"));
        assertFalse(AgentChatCommandClassifier.isFarmHereCommand("setting up camp"));
    }

    @Test
    void shouldParseNamedItemGiveRequests() {
        assertEquals("name:flaming feather", AgentTradeDialogueClassifier.matchChoiceCategory("give me flaming feather"));
        assertEquals("name:flaming feather", AgentTradeDialogueClassifier.matchChoiceCategory("give flaming feather"));
    }

    @Test
    void shouldParseRecommendedGearTrades() {
        assertEquals("recommended", AgentTradeDialogueClassifier.matchTradeCategory("trade recommended gear"));
        assertEquals("recommended", AgentTradeDialogueClassifier.matchTradeCategory("trade me upgrades"));
        assertEquals("recommended", AgentTradeDialogueClassifier.matchTradeCategory("trade better equipment"));
    }

    @Test
    void shouldParseAmmoTrades() {
        assertEquals("ammo", AgentTradeDialogueClassifier.matchTradeCategory("trade ammo"));
        assertEquals("ammo", AgentTradeDialogueClassifier.matchTradeCategory("trade me your arrows"));
        assertEquals("ammo", AgentTradeDialogueClassifier.matchTradeCategory("trade bullets"));
    }

    @Test
    void shouldParseReservedEquipTradesWithOptionalPage() {
        assertEquals("equips:reserved:1", AgentTradeDialogueClassifier.matchTradeCategory("trade reserve"));
        assertEquals("equips:reserved:1", AgentTradeDialogueClassifier.matchTradeCategory("trade reserved"));
        assertEquals("equips:reserved:3", AgentTradeDialogueClassifier.matchTradeCategory("trade reserve 3"));
        assertEquals("equips:reserved:12", AgentTradeDialogueClassifier.matchTradeCategory("trade me your reserve 12"));
    }

    @Test
    void shouldParseTrashGearTrades() {
        assertEquals("trash", AgentTradeDialogueClassifier.matchTradeCategory("trade trash"));
        assertEquals("trash", AgentTradeDialogueClassifier.matchTradeCategory("trade my trash"));
        assertEquals("trash", AgentTradeDialogueClassifier.matchTradeCategory("trade junk"));
        assertEquals("trash", AgentTradeDialogueClassifier.matchTradeCategory("got trash?"));
        assertEquals("trash", AgentTradeDialogueClassifier.matchTradeCategory("have any junk?"));
        assertNull(AgentTradeDialogueClassifier.matchItemQuery("got trash?"));
        assertEquals("trash", AgentTradeDialogueClassifier.matchTradeCategory("show me your junk"));
        assertEquals("trash", AgentTradeDialogueClassifier.matchTradeCategory("show your junk"));
        assertEquals("trash", AgentTradeDialogueClassifier.matchTradeCategory("show ur junk"));
    }

    @Test
    void shouldNotParseSellTrashAsTradeTrash() {
        assertNull(AgentTradeDialogueClassifier.matchTradeCategory("sell trash"));
        assertNull(AgentTradeDialogueClassifier.matchTradeCategory("sell junk"));
    }

    @Test
    void shouldMatchMesoQueries() {
        assertTrue(AgentChatCommandClassifier.isMesoQuery("meso?"));
        assertTrue(AgentChatCommandClassifier.isMesoQuery("mesos?"));
        assertTrue(AgentChatCommandClassifier.isMesoQuery("cash?"));
        assertTrue(AgentChatCommandClassifier.isMesoQuery("how much cash do you have"));
        assertTrue(AgentChatCommandClassifier.isMesoQuery("your mesos"));
        assertFalse(AgentChatCommandClassifier.isMesoQuery("trade mesos"));
    }

    @Test
    void shouldMatchMovementStatQueries() {
        assertTrue(AgentChatCommandClassifier.isMovementStatsQuery("speed?"));
        assertTrue(AgentChatCommandClassifier.isMovementStatsQuery("jump?"));
        assertTrue(AgentChatCommandClassifier.isMovementStatsQuery("movement stats"));
        assertTrue(AgentChatCommandClassifier.isMovementStatsQuery("how fast are you"));
        assertFalse(AgentChatCommandClassifier.isMovementStatsQuery("trade mesos"));
    }

    @Test
    void shouldTriggerGreetingFidgetHalfTheTimeWhileFollowing() {
        BotEntry entry = new BotEntry(null, null, null);
        entry.following = true;

        assertTrue(BotFidgetManager.maybeStartGreetingFidget(entry, 0));
        assertFalse(entry.fidgetMode == BotFidgetMode.NONE);
        assertEquals(BotFidgetTrigger.SOCIAL, entry.fidgetTrigger);

        BotFidgetManager.clear(entry);

        assertFalse(BotFidgetManager.maybeStartGreetingFidget(entry, 99));
        assertEquals(BotFidgetMode.NONE, entry.fidgetMode);
    }

    @Test
    void shouldTriggerFidgetCommandWithoutGreetingRoll() {
        BotEntry entry = new BotEntry(null, null, null);
        entry.following = true;

        assertTrue(AgentChatCommandClassifier.isFidgetCommand("fidget"));
        assertTrue(AgentChatCommandClassifier.isFidgetCommand("fidget!"));
        assertFalse(AgentChatCommandClassifier.isFidgetCommand("please fidget"));
        for (int i = 0; i < 100; i++) {
            assertTrue(Set.of(2, 3, 5, 6, 7).contains(AgentBotChatStatusRuntime.randomFidgetExpression()));
        }

        assertTrue(BotFidgetManager.maybeStartSocialFidget(entry));
        assertFalse(entry.fidgetMode == BotFidgetMode.NONE);
        assertEquals(BotFidgetTrigger.SOCIAL, entry.fidgetTrigger);
    }

    @Test
    void shouldTrackPerScrollerStreaksAndDisableHundredPercentStreakChats() {
        BotEntry entry = new BotEntry(null, null, null);
        long start = 2_000_000L;
        int alice = 101;
        int bob = 202;

        assertEquals(1, BotScrollReactionManager.updateReactionStreak(entry, alice, true, start));
        assertEquals(2, BotScrollReactionManager.updateReactionStreak(entry, alice, true, start + 30_000L));
        assertEquals(3, BotScrollReactionManager.updateReactionStreak(entry, alice, true, start + 60_000L));

        assertEquals(1, BotScrollReactionManager.updateReactionStreak(entry, bob, true, start + 10_000L));
        assertEquals(1, BotScrollReactionManager.updateReactionStreak(entry, alice, false, start + 90_000L));
        assertEquals(2, BotScrollReactionManager.updateReactionStreak(entry, alice, false, start + 120_000L));
        assertEquals(1, BotScrollReactionManager.updateReactionStreak(
                entry, alice, false, start + 120_000L + BotScrollReactionManager.streakWindowMs() + 1L));
    }

    @Test
    void shouldNotTriggerGreetingFidgetWhileOwnerIsAfk() {
        BotEntry entry = new BotEntry(null, null, null);
        entry.following = true;
        AgentBotActivityStateRuntime.setOwnerWasAfk(entry, true);

        assertFalse(BotFidgetManager.maybeStartGreetingFidget(entry, 0));
        assertEquals(BotFidgetMode.NONE, entry.fidgetMode);
    }

    @Test
    void shouldShowBuffDebugStateWithEnabledAndMode() {
        BotEntry entry = new BotEntry(null, null, null);

        entry.buffConsumablesEnabled = true;
        entry.buffCheapMode = true;
        assertEquals("buff on(cheap)", BotBuffManager.formatDebugState(entry));

        entry.buffConsumablesEnabled = false;
        entry.buffCheapMode = false;
        assertEquals("buff off(best)", BotBuffManager.formatDebugState(entry));
    }

    @Test
    void shouldParseProactiveOfferToggleCommands() {
        assertTrue(AgentChatCommandClassifier.isProactiveOffersOnCommand("proactive offers on"));
        assertTrue(AgentChatCommandClassifier.isProactiveOffersOnCommand("future upgrades on"));
        assertTrue(AgentChatCommandClassifier.isProactiveOffersOffCommand("proactive offers off"));
        assertTrue(AgentChatCommandClassifier.isProactiveOffersOffCommand("offers future off"));
        assertFalse(AgentChatCommandClassifier.isProactiveOffersOnCommand("trade recommended gear"));
    }

    @Test
    void shouldParseOwnerGearNeedQuestionsAsUpgradeRequests() {
        assertTrue(AgentChatCommandClassifier.isRequestUpgradeCommand("do you need any gear from me?"));
        assertTrue(AgentChatCommandClassifier.isRequestUpgradeCommand("need gear from me"));
        assertTrue(AgentChatCommandClassifier.isRequestUpgradeCommand("do you need equipment"));
        assertFalse(AgentChatCommandClassifier.isRequestUpgradeCommand("trade recommended gear"));
    }

    @Test
    void shouldBuildMovementStatsReportUsingGameStatsAndDerivedPhysics() {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(bot.getMap()).thenReturn(map);
        when(bot.getTotalMoveSpeedStat()).thenReturn(120);
        when(bot.getTotalJumpStat()).thenReturn(110);
        BotMovementProfile profile = BotMovementProfile.fromCharacter(bot);

        List<String> report = AgentBotChatReportRuntime.buildMovementStatsReport(bot);

        assertEquals(List.of(
                "speed 120% jump 110%",
                String.format(Locale.ROOT, "walk %.1f px/s, %d px/tick, climb %d, hforce %.1f",
                        profile.walkVelocityPxs(),
                        BotMovementManager.walkStep(map, profile),
                        BotPhysicsEngine.climbStepPerTick(),
                        profile.hForcePxs()),
                String.format(Locale.ROOT, "jump %.1f, rope %.1f, max %.1f px, reach %d/%d px",
                        BotPhysicsEngine.jumpForcePerTick(profile),
                        BotPhysicsEngine.ropeJumpForcePerTick(profile),
                        BotPhysicsEngine.calculateMaxJumpHeight(profile),
                        BotPhysicsEngine.maxJumpHorizontalTravel(map, profile),
                        BotPhysicsEngine.maxRopeJumpHorizontalTravel(map, profile))
        ), report);
    }

    @Test
    void shouldReportForcedMovementStatsOnMovementSkillLimitMaps() {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(map.getFieldLimit()).thenReturn((int) FieldLimit.MOVEMENTSKILLS.getValue());
        when(bot.getMap()).thenReturn(map);
        when(bot.getTotalMoveSpeedStat()).thenReturn(140);
        when(bot.getTotalJumpStat()).thenReturn(125);

        List<String> report = AgentBotChatReportRuntime.buildMovementStatsReport(bot);

        assertEquals("speed 100% jump 100% (map forced; raw 140%/125%)", report.getFirst());
    }

    @Test
    void shouldBuildPhysicalRangeReportFromEffectiveTotals() {
        Character bot = mock(Character.class);
        Inventory equipped = mock(Inventory.class);
        when(bot.getJob()).thenReturn(Job.FIGHTER);
        when(bot.getLevel()).thenReturn(48);
        when(bot.getTotalWatk()).thenReturn(20);
        when(bot.getTotalDex()).thenReturn(100);
        when(bot.getTotalLuk()).thenReturn(40);
        when(bot.getInventory(client.inventory.InventoryType.EQUIPPED)).thenReturn(equipped);
        when(equipped.getItem((short) -11)).thenReturn(null);
        when(equipped.iterator()).thenReturn(List.<client.inventory.Item>of().iterator());
        when(bot.calculateMinBaseDamage(20, 0.1d)).thenReturn(50);
        when(bot.calculateMaxBaseDamage(20)).thenReturn(99);

        String report = AgentBotChatReportRuntime.buildRangeReport(bot,
                new BotEquipManager.MapDamageProfile(100, 40, 48));

        assertEquals("my dmg is 50-99, watk 20, acc 100 | hit 47% vs hardest mob (avd 40)", report);
    }

    @Test
    void shouldBuildMageRangeReportFromEffectiveMagicTotals() {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(Job.MAGICIAN);
        when(bot.getLevel()).thenReturn(50);
        when(bot.getTotalMagic()).thenReturn(200);
        when(bot.getTotalInt()).thenReturn(100);
        when(bot.getTotalLuk()).thenReturn(50);

        String report = AgentBotChatReportRuntime.buildRangeReport(bot,
                new BotEquipManager.MapDamageProfile(100, 30, 50));

        assertEquals("my dmg is 3-9, matk 200, magic acc 75 | hit 26% vs hardest mob (avd 30)", report);
    }

    @Test
    void shouldBuildOwnerLootOfferPrompt() {
        String prompt = BotOfferManager.buildLootOfferPrompt("Owner", "Blue Moon", true);
        assertTrue(Set.of(
                "Owner, I have Blue Moon, you want?",
                "Owner, picked up Blue Moon, want it?",
                "Owner, I got Blue Moon if you want it",
                "Owner, want Blue Moon?",
                "Owner, I can trade you Blue Moon",
                "Owner, grabbed Blue Moon for you if you want it").contains(prompt));
    }

    @Test
    void shouldBuildPartyLootOfferPrompt() {
        String prompt = BotOfferManager.buildLootOfferPrompt("Alice", "Blue Moon", false);
        assertTrue(Set.of(
                "Alice, I have Blue Moon, you want?",
                "Alice, picked up Blue Moon, want it?",
                "Alice, I got Blue Moon if you want it",
                "Alice, want Blue Moon?",
                "Alice, I can trade you Blue Moon",
                "Alice, grabbed Blue Moon for you if you want it").contains(prompt));
    }

    @Test
    void shouldMatchRespecCommands() {
        assertTrue(AgentChatCommandClassifier.isRespecCommand("respec"));
        assertTrue(AgentChatCommandClassifier.isRespecCommand("reset skills"));
        assertTrue(AgentChatCommandClassifier.isRespecCommand("rebuild sp"));
    }

    @Test
    void shouldMatchApRespecCommands() {
        assertTrue(AgentChatCommandClassifier.isApRespecCommand("respec ap"));
        assertTrue(AgentChatCommandClassifier.isApRespecCommand("reset ap"));
        assertTrue(AgentChatCommandClassifier.isApRespecCommand("rebuild ap"));
        assertFalse(AgentChatCommandClassifier.isApRespecCommand("respec"));
    }

    @Test
    void shouldMarkQueuedRepliesAsOwnerDirected() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotMessageQueueStateRuntime.setSending(entry, true);

        AgentBotReplyRuntime.queueReply(entry, "owner reply");
        AgentBotReplyRuntime.queueSay(entry, "party chatter");

        AgentQueuedMessage first = AgentBotMessageQueueStateRuntime.queue(entry).poll();
        AgentQueuedMessage second = AgentBotMessageQueueStateRuntime.queue(entry).poll();
        assertEquals("owner reply", first.text());
        assertTrue(first.ownerDirected());
        assertEquals("party chatter", second.text());
        assertFalse(second.ownerDirected());
    }

    @Test
    void shouldQueueHelpAsOwnerDirectedReply() throws Exception {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotMessageQueueStateRuntime.setSending(entry, true);

        AgentBotChatReportRuntime.reportHelp(entry);

        assertEquals(5, AgentBotMessageQueueStateRuntime.queue(entry).size());
        for (AgentQueuedMessage message : AgentBotMessageQueueStateRuntime.queue(entry)) {
            assertTrue(message.ownerDirected());
        }
    }

    @Test
    void shouldClearPendingOfferStateForOwnerAsk() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotPendingActionStateRuntime.setPendingDropCategory(entry, "equips");
        entry.pendingLootOfferItem = new Item(1002000, (short) 1, (short) 1);
        entry.pendingLootOfferRecipientId = 123;
        entry.pendingLootOfferExpiresAt = Long.MAX_VALUE;
        entry.pendingLootOfferBotRequesting = true;
        AgentBotOfferStateRuntime.reserveGearPrompt(entry, Long.MAX_VALUE);

        BotOfferManager.clearPendingOfferForOwnerAsk(entry);

        assertNull(AgentBotPendingActionStateRuntime.pendingDropCategory(entry));
        assertNull(entry.pendingLootOfferItem);
        assertEquals(0, entry.pendingLootOfferRecipientId);
        assertEquals(0L, entry.pendingLootOfferExpiresAt);
        assertFalse(entry.pendingLootOfferBotRequesting);
        assertEquals(0L, entry.pendingGearPromptAt());
    }
}
