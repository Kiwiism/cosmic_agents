package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;
import server.bots.BotChatManager;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDialogueCatalogTest {
    @Test
    void botChatManagerUsesAgentDialogueCatalogPools() throws Exception {
        assertSame(AgentDialogueCatalog.followReplies(), botChatPool("FOLLOW_REPLIES"));
        assertSame(AgentDialogueCatalog.moveHereReplies(), botChatPool("MOVE_HERE_REPLIES"));
        assertSame(AgentDialogueCatalog.stopReplies(), botChatPool("STOP_REPLIES"));
        assertSame(AgentDialogueCatalog.greetingReplies(), botChatPool("GREETING_REPLIES"));
        assertSame(AgentDialogueCatalog.welcomeBackReplies(), botChatPool("WB_REPLIES"));
        assertSame(AgentDialogueCatalog.mesoReplies(), botChatPool("MESO_REPLIES"));
    }

    @Test
    void catalogPreservesExpectedLegacyLines() {
        assertTrue(AgentDialogueCatalog.followReplies().contains("w8 up"));
        assertTrue(AgentDialogueCatalog.dropOrTradePrompts().contains("got %s, want me to trade or drop?"));
        assertTrue(AgentDialogueCatalog.ownerPotShortageReplies().contains("we're low on %s pots too, boss"));
        assertTrue(AgentDialogueCatalog.fameOkReplies().contains("famed %s"));
        assertTrue(AgentDialogueCatalog.fameTargetNotFoundReply("Alice").contains("Alice on the map"));
        assertTrue(AgentDialogueCatalog.fameSelfReply().contains("fame myself"));
        assertTrue(AgentDialogueCatalog.fameTooLowLevelReply().contains("too low level"));
        assertTrue(AgentDialogueCatalog.fameFailedReply().contains("fame failed"));
        assertTrue(AgentDialogueCatalog.keepDropChoiceReply().contains("keeping them"));
        assertEquals("ok! keeping them", AgentDialogueCatalog.pendingActionCancelReply(true));
        assertEquals("ok nvm, staying!", AgentDialogueCatalog.pendingActionCancelReply(false));
        assertTrue(AgentDialogueCatalog.noJobSkillsReply().contains("no job skills"));
        assertTrue(AgentDialogueCatalog.noJobSkillsWithSpReply(12).contains("12 SP left"));
        assertTrue(AgentDialogueCatalog.noBeginnerSkillsReply(3).contains("3 beginner SP left"));
        assertTrue(AgentDialogueCatalog.noLearnedSkillsInReply("warrior").contains("warrior"));
        assertTrue(AgentDialogueCatalog.noCritPassiveReply().contains("can't crit"));
        assertTrue(AgentDialogueCatalog.weirdTransferReply().contains("weird"));
        assertTrue(AgentDialogueCatalog.welcomeBackOfflinePartyTemplates().contains("wb!! we're at %s"));
        assertTrue(AgentDialogueCatalog.relogConfirmPrompts().contains("save and relog? type yes"));
        assertTrue(AgentDialogueCatalog.logoutConfirmedReplies().contains("cya!!"));
        assertTrue(AgentDialogueCatalog.awayTownOrLogoutPrompt().contains("nearest town or logout"));
        assertTrue(AgentDialogueCatalog.awayStayOrLogoutPrompt().contains("stay safe here or logout"));
        assertTrue(AgentDialogueCatalog.awayLogoutConfirmReply().contains("logging us out"));
        assertTrue(AgentDialogueCatalog.awayTownConfirmReply().contains("heading to town"));
        assertTrue(AgentDialogueCatalog.awayStayConfirmReply().contains("staying safe here"));
        assertTrue(AgentDialogueCatalog.awayCancelReply().contains("staying with you"));
        assertTrue(AgentDialogueCatalog.supportOffReply().contains("skill buffs off"));
        assertTrue(AgentDialogueCatalog.supportOnReply().contains("skill buffs on"));
        assertTrue(AgentDialogueCatalog.healsOffReply().contains("no heals"));
        assertTrue(AgentDialogueCatalog.healsOnReply().contains("heal when needed"));
        assertTrue(AgentDialogueCatalog.buffConsumablesOffReply().contains("no buff pots"));
        assertTrue(AgentDialogueCatalog.buffConsumablesOnReply("cheap").contains("(cheap)"));
        assertTrue(AgentDialogueCatalog.buffConsumablesCheapReply().contains("cheapest buff pots"));
        assertTrue(AgentDialogueCatalog.buffConsumablesMaxReply().contains("best buff pots"));
        assertTrue(AgentDialogueCatalog.proactiveOffersOffReply().contains("immediate upgrades"));
        assertTrue(AgentDialogueCatalog.proactiveOffersOnReply().contains("proactive upgrade offers on"));
        assertTrue(AgentDialogueCatalog.oneHandedSpVariantReply().contains("1h sword build"));
        assertTrue(AgentDialogueCatalog.twoHandedSpVariantReply().contains("2h build"));
        assertTrue(AgentDialogueCatalog.gearOptimizedReply().contains("gear optimized"));
        assertTrue(AgentDialogueCatalog.gearCheckUnavailableReply().contains("can't check your gear"));
        assertTrue(AgentDialogueCatalog.noBetterGearReply().contains("no better gear"));
        assertTrue(AgentDialogueCatalog.helpLines().contains("gear: ask 'any upgrades?' or say 'trade recommended gear'"));
        assertTrue(AgentDialogueCatalog.helpLines().contains("trade: mesos, scrolls, pots, equips, etc, or named items"));
        assertTrue(AgentDialogueCatalog.jobChangeReplyTemplates().contains("ok %s it is!"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> botChatPool(String fieldName) throws Exception {
        Field field = BotChatManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (List<String>) field.get(null);
    }
}
