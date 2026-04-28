package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
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
        assertEquals("mesos", BotChatManager.matchTradeCategory("trade mesos"));
        assertEquals("mesos", BotChatManager.matchTradeCategory("trade me all your mesos"));
    }

    @Test
    void shouldParseTradeMesosWithExplicitAmounts() {
        assertEquals("mesos:1000000", BotChatManager.matchTradeCategory("trade 1m mesos"));
        assertEquals("mesos:1250000", BotChatManager.matchTradeCategory("trade 1,250,000 mesos"));
        assertEquals("mesos:1500000", BotChatManager.matchTradeCategory("trade 1.5m mesos"));
    }

    @Test
    void shouldParseAdditionalMesoTransferPhrasings() {
        assertEquals("mesos:5000000", BotChatManager.matchTradeCategory("give me 5m"));
        assertEquals("mesos:200000", BotChatManager.matchTradeCategory("gimme 200000"));
        assertEquals("mesos", BotChatManager.matchTradeCategory("trade meso"));
        assertEquals("mesos:10000000", BotChatManager.matchTradeCategory("give meso 10m"));
        assertEquals("mesos:10000000", BotChatManager.matchTradeCategory("trade 10m"));
    }

    @Test
    void shouldStillParseNamedItemTrades() {
        assertEquals("name:chaos scroll", BotChatManager.matchTradeCategory("trade chaos scroll"));
        assertEquals("name:chaos scroll", BotChatManager.matchTradeCategory("trade chaos scrolls"));
    }

    @Test
    void shouldParseViewEquipmentRequestsAsTradeCommands() {
        assertEquals("name:hat", BotChatManager.matchTradeCategory("show me your hat"));
        assertEquals("name:ring 2", BotChatManager.matchTradeCategory("let me see ur ring 2"));
        assertEquals("name:weapon", BotChatManager.matchTradeCategory("can i c yo weapon"));
    }

    @Test
    void shouldParseFollowTargetCommandsWithoutBreakingPlainFollow() {
        assertEquals("clawer", BotChatManager.matchFollowTarget("follow clawer"));
        assertEquals("Clawer", BotChatManager.matchFollowTarget("follow Clawer please"));
        assertNull(BotChatManager.matchFollowTarget("follow me"));
        assertNull(BotChatManager.matchFollowTarget("follow here"));
    }

    @Test
    void shouldParseNamedItemGiveRequests() {
        assertEquals("name:flaming feather", BotChatManager.matchChoiceCategory("give me flaming feather"));
        assertEquals("name:flaming feather", BotChatManager.matchChoiceCategory("give flaming feather"));
    }

    @Test
    void shouldParseRecommendedGearTrades() {
        assertEquals("recommended", BotChatManager.matchTradeCategory("trade recommended gear"));
        assertEquals("recommended", BotChatManager.matchTradeCategory("trade me upgrades"));
        assertEquals("recommended", BotChatManager.matchTradeCategory("trade better equipment"));
    }

    @Test
    void shouldMatchMesoQueries() {
        assertTrue(BotChatManager.isMesoQuery("meso?"));
        assertTrue(BotChatManager.isMesoQuery("mesos?"));
        assertTrue(BotChatManager.isMesoQuery("cash?"));
        assertTrue(BotChatManager.isMesoQuery("how much cash do you have"));
        assertTrue(BotChatManager.isMesoQuery("your mesos"));
        assertFalse(BotChatManager.isMesoQuery("trade mesos"));
    }

    @Test
    void shouldMatchMovementStatQueries() {
        assertTrue(BotChatManager.isMovementStatsQuery("speed?"));
        assertTrue(BotChatManager.isMovementStatsQuery("jump?"));
        assertTrue(BotChatManager.isMovementStatsQuery("movement stats"));
        assertTrue(BotChatManager.isMovementStatsQuery("how fast are you"));
        assertFalse(BotChatManager.isMovementStatsQuery("trade mesos"));
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

        assertTrue(BotChatManager.isFidgetCommand("fidget"));
        assertTrue(BotChatManager.isFidgetCommand("fidget!"));
        assertFalse(BotChatManager.isFidgetCommand("please fidget"));
        for (int i = 0; i < 100; i++) {
            assertTrue(Set.of(2, 3, 5, 6, 7).contains(BotChatManager.randomFidgetExpression()));
        }

        assertTrue(BotFidgetManager.maybeStartSocialFidget(entry));
        assertFalse(entry.fidgetMode == BotFidgetMode.NONE);
        assertEquals(BotFidgetTrigger.SOCIAL, entry.fidgetTrigger);
    }

    @Test
    void shouldNotTriggerGreetingFidgetWhileOwnerIsAfk() {
        BotEntry entry = new BotEntry(null, null, null);
        entry.following = true;
        entry.ownerWasAfk = true;

        assertFalse(BotFidgetManager.maybeStartGreetingFidget(entry, 0));
        assertEquals(BotFidgetMode.NONE, entry.fidgetMode);
    }

    @Test
    void shouldFormatCompactMesos() {
        assertEquals("999", BotChatManager.formatCompactMesos(999));
        assertEquals("6k", BotChatManager.formatCompactMesos(6_000));
        assertEquals("3.5k", BotChatManager.formatCompactMesos(3_500));
        assertEquals("2.1m", BotChatManager.formatCompactMesos(2_100_000));
    }

    @Test
    void shouldBuildMesoReportUsingCompactAmounts() {
        assertTrue(BotChatManager.buildMesoReport(6_000).contains("6k"));
        assertTrue(BotChatManager.buildMesoReport(3_500).contains("3.5k"));
        assertTrue(BotChatManager.buildMesoReport(2_100_000).contains("2.1m"));
    }

    @Test
    void shouldBuildMovementStatsReportUsingGameStatsAndDerivedPhysics() {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(bot.getMap()).thenReturn(map);
        when(bot.getTotalMoveSpeedStat()).thenReturn(120);
        when(bot.getTotalJumpStat()).thenReturn(110);
        BotMovementProfile profile = BotMovementProfile.fromCharacter(bot);

        List<String> report = BotChatManager.buildMovementStatsReport(bot);

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

        List<String> report = BotChatManager.buildMovementStatsReport(bot);

        assertEquals("speed 100% jump 100% (map forced; raw 140%/125%)", report.getFirst());
    }

    @Test
    void shouldBuildOwnerLootOfferPrompt() {
        String prompt = BotOfferManager.buildLootOfferPrompt("Owner", "Blue Moon", true);
        assertTrue(Set.of(
                "I have Blue Moon, you want?",
                "picked up Blue Moon, want it?",
                "I got Blue Moon for you, want?").contains(prompt));
    }

    @Test
    void shouldBuildPartyLootOfferPrompt() {
        String prompt = BotOfferManager.buildLootOfferPrompt("Alice", "Blue Moon", false);
        assertTrue(Set.of(
                "Alice, I have Blue Moon, you want?",
                "Alice, picked up Blue Moon, want it?",
                "Alice, I got Blue Moon if you want it").contains(prompt));
    }

    @Test
    void shouldMatchRespecCommands() {
        assertTrue(BotChatManager.isRespecCommand("respec"));
        assertTrue(BotChatManager.isRespecCommand("reset skills"));
        assertTrue(BotChatManager.isRespecCommand("rebuild sp"));
    }

    @Test
    void shouldMatchApRespecCommands() {
        assertTrue(BotChatManager.isApRespecCommand("respec ap"));
        assertTrue(BotChatManager.isApRespecCommand("reset ap"));
        assertTrue(BotChatManager.isApRespecCommand("rebuild ap"));
        assertFalse(BotChatManager.isApRespecCommand("respec"));
    }
}
