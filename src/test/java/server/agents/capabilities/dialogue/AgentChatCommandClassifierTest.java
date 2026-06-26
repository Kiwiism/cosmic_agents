package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatCommandClassifierTest {
    @Test
    void shouldNormalizeCommandTextExactlyLikeLegacyChat() {
        assertEquals("", AgentChatCommandClassifier.normalizeCommandText(null));
        assertEquals("stop", AgentChatCommandClassifier.normalizeCommandText("  !!!please stop?? "));
        assertEquals("follow me", AgentChatCommandClassifier.normalizeCommandText("yo can you follow me please"));
        assertEquals("go town", AgentChatCommandClassifier.normalizeCommandText("pls pls go town pls"));
        assertEquals("save and logout", AgentChatCommandClassifier.normalizeCommandText("...save   and   logout!!!"));
    }

    @Test
    void shouldParseFollowTargetCommandsWithoutBreakingPlainFollow() {
        assertEquals("clawer", AgentChatCommandClassifier.matchFollowTarget("follow clawer"));
        assertEquals("Clawer", AgentChatCommandClassifier.matchFollowTarget("follow Clawer please"));
        assertNull(AgentChatCommandClassifier.matchFollowTarget("follow me"));
        assertNull(AgentChatCommandClassifier.matchFollowTarget("follow here"));
        assertNull(AgentChatCommandClassifier.matchFollowTarget("follow pls"));
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

        assertTrue(AgentChatCommandClassifier.isFidgetCommand("fidget"));
        assertTrue(AgentChatCommandClassifier.isFidgetCommand("fidget!"));
        assertFalse(AgentChatCommandClassifier.isFidgetCommand("please fidget"));
    }

    @Test
    void shouldClassifySupplyRequests() {
        assertTrue(AgentChatCommandClassifier.isNeedPotCommand("nned pot"));
        assertTrue(AgentChatCommandClassifier.isNeedPotCommand("need some pots"));
        assertTrue(AgentChatCommandClassifier.isNeedPotCommand("anybody got pot"));
        assertTrue(AgentChatCommandClassifier.isNeedPotCommand("low on pots"));
        assertTrue(AgentChatCommandClassifier.isNeedHpPotCommand("anyone have hp pots"));
        assertTrue(AgentChatCommandClassifier.isNeedMpPotCommand("running low on mana potions"));
        assertTrue(AgentChatCommandClassifier.isNeedAmmoCommand("anybody got arrows"));
        assertTrue(AgentChatCommandClassifier.isNeedAmmoCommand("low on ammo"));

        assertTrue(AgentChatCommandClassifier.isGroupSupplyRequest("anybody got arrows"));
        assertFalse(AgentChatCommandClassifier.isGroupSupplyRequest("trade mesos"));
    }

    @Test
    void shouldMatchMesoAndMovementQueries() {
        assertTrue(AgentChatCommandClassifier.isMesoQuery("meso?"));
        assertTrue(AgentChatCommandClassifier.isMesoQuery("mesos?"));
        assertTrue(AgentChatCommandClassifier.isMesoQuery("cash?"));
        assertTrue(AgentChatCommandClassifier.isMesoQuery("how much cash do you have"));
        assertTrue(AgentChatCommandClassifier.isMesoQuery("your mesos"));
        assertFalse(AgentChatCommandClassifier.isMesoQuery("trade mesos"));

        assertTrue(AgentChatCommandClassifier.isMovementStatsQuery("speed?"));
        assertTrue(AgentChatCommandClassifier.isMovementStatsQuery("jump?"));
        assertTrue(AgentChatCommandClassifier.isMovementStatsQuery("movement stats"));
        assertTrue(AgentChatCommandClassifier.isMovementStatsQuery("how fast are you"));
        assertFalse(AgentChatCommandClassifier.isMovementStatsQuery("trade mesos"));
    }

    @Test
    void shouldMatchProfileAndOfferToggles() {
        assertTrue(AgentChatCommandClassifier.isProactiveOffersOnCommand("proactive offers on"));
        assertTrue(AgentChatCommandClassifier.isProactiveOffersOnCommand("future upgrades on"));
        assertTrue(AgentChatCommandClassifier.isProactiveOffersOffCommand("proactive offers off"));
        assertTrue(AgentChatCommandClassifier.isProactiveOffersOffCommand("offers future off"));
        assertFalse(AgentChatCommandClassifier.isProactiveOffersOnCommand("trade recommended gear"));

        assertTrue(AgentChatCommandClassifier.isRequestUpgradeCommand("do you need any gear from me?"));
        assertTrue(AgentChatCommandClassifier.isRequestUpgradeCommand("need gear from me"));
        assertTrue(AgentChatCommandClassifier.isRequestUpgradeCommand("do you need equipment"));
        assertFalse(AgentChatCommandClassifier.isRequestUpgradeCommand("trade recommended gear"));

        assertTrue(AgentChatCommandClassifier.isRespecCommand("respec"));
        assertTrue(AgentChatCommandClassifier.isRespecCommand("reset skills"));
        assertTrue(AgentChatCommandClassifier.isRespecCommand("rebuild sp"));
        assertTrue(AgentChatCommandClassifier.isApRespecCommand("respec ap"));
        assertTrue(AgentChatCommandClassifier.isApRespecCommand("reset ap"));
        assertTrue(AgentChatCommandClassifier.isApRespecCommand("rebuild ap"));
        assertFalse(AgentChatCommandClassifier.isApRespecCommand("respec"));
    }

    @Test
    void shouldMatchReportAndDebugQueries() {
        assertTrue(AgentChatCommandClassifier.isHelpCommand("help"));
        assertTrue(AgentChatCommandClassifier.isRecommendedGearQuery("any upgrades?"));
        assertTrue(AgentChatCommandClassifier.isSkillsQuery("skills?"));
        assertTrue(AgentChatCommandClassifier.isStatsQuery("your stats"));
        assertTrue(AgentChatCommandClassifier.isRangeQuery("what's your range"));
        assertTrue(AgentChatCommandClassifier.isBuildQuery("what's your build"));
        assertTrue(AgentChatCommandClassifier.isInventoryQuery("show your inventory"));
        assertTrue(AgentChatCommandClassifier.isExpQuery("exp?"));
        assertTrue(AgentChatCommandClassifier.isInventorySlotsQuery("slots left"));
        assertTrue(AgentChatCommandClassifier.isScrollsQuery("any scrolls"));
        assertTrue(AgentChatCommandClassifier.isPotionsQuery("pots left"));
        assertTrue(AgentChatCommandClassifier.isDebugStatsQuery("debug stats"));
        assertTrue(AgentChatCommandClassifier.isCritDebugQuery("crit stats?"));
        assertTrue(AgentChatCommandClassifier.isPotDebugQuery("potion debug"));
        assertTrue(AgentChatCommandClassifier.isBuffListQuery("buff list"));
        assertTrue(AgentChatCommandClassifier.isBuffDebugQuery("active buffs"));
        assertTrue(AgentChatCommandClassifier.isSkillBuffDebugQuery("skill buffs debug"));
    }

    @Test
    void shouldClassifySupportHealAndBuffToggles() {
        assertTrue(AgentChatCommandClassifier.isSupportOnCommand("support party"));
        assertTrue(AgentChatCommandClassifier.isSupportOnCommand("skill buffs on"));
        assertTrue(AgentChatCommandClassifier.isSupportOffCommand("stop buffing"));
        assertTrue(AgentChatCommandClassifier.isSupportOffCommand("no skill buffs"));

        assertTrue(AgentChatCommandClassifier.isHealsOnCommand("auto heals"));
        assertTrue(AgentChatCommandClassifier.isHealsOnCommand("heal us"));
        assertTrue(AgentChatCommandClassifier.isHealsOffCommand("stop healing"));
        assertTrue(AgentChatCommandClassifier.isHealsOffCommand("no heals"));

        assertTrue(AgentChatCommandClassifier.isBuffConsumablesOnCommand("buff pots on"));
        assertTrue(AgentChatCommandClassifier.isBuffConsumablesOnCommand("auto buff pot"));
        assertTrue(AgentChatCommandClassifier.isBuffConsumablesOffCommand("buff pots off"));
        assertTrue(AgentChatCommandClassifier.isBuffConsumablesOffCommand("no buff pots"));
        assertTrue(AgentChatCommandClassifier.isBuffConsumablesCheapCommand("buff cheap"));
        assertTrue(AgentChatCommandClassifier.isBuffConsumablesMaxCommand("buff pots max"));
        assertTrue(AgentChatCommandClassifier.isBuffConsumablesMaxCommand("buff good"));
    }

    @Test
    void shouldClassifySessionRequestsAndConfirmations() {
        assertTrue(AgentChatCommandClassifier.isRelogRequest("save and relog"));
        assertTrue(AgentChatCommandClassifier.isRelogRequest("gotta reconnect"));
        assertTrue(AgentChatCommandClassifier.isLogoutRequest("save and logout"));
        assertTrue(AgentChatCommandClassifier.isLogoutRequest("log me off"));
        assertTrue(AgentChatCommandClassifier.isAwayRequest("gtg"));
        assertTrue(AgentChatCommandClassifier.isAwayRequest("back in a min"));
        assertFalse(AgentChatCommandClassifier.isAwayRequest("lets go grind"));

        assertTrue(AgentChatCommandClassifier.isLogoutConfirm("yes, do it"));
        assertTrue(AgentChatCommandClassifier.isAwayTownConfirm("go to town"));
        assertTrue(AgentChatCommandClassifier.isAwayStayConfirm("stay here"));
        assertTrue(AgentChatCommandClassifier.isAwayLogoutConfirm("save and logout"));
        assertTrue(AgentChatCommandClassifier.isNegativeConfirm("nope"));
    }
}
