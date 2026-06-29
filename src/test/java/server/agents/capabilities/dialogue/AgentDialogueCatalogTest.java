package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDialogueCatalogTest {
    @Test
    void catalogPreservesExpectedLegacyLines() {
        assertTrue(AgentDialogueCatalog.followReplies().contains("w8 up"));
        assertTrue(AgentDialogueCatalog.moveHereReplies().contains("k, coming"));
        assertTrue(AgentDialogueCatalog.stopReplies().contains("stopping"));
        assertTrue(AgentDialogueCatalog.grindReplies().contains("farming time"));
        assertTrue(AgentDialogueCatalog.greetingReplies().contains("hi"));
        assertTrue(AgentDialogueCatalog.welcomeBackReplies().contains("wb"));
        assertTrue(AgentDialogueCatalog.tradeInviteReplies().contains("coming to trade"));
        assertTrue(AgentDialogueCatalog.tradeInvitationReplies().contains("opening trade"));
        assertTrue(AgentDialogueCatalog.tradeThanksReplies().contains("legend"));
        assertTrue(AgentDialogueCatalog.tradeFreebieReplies().contains("free delivery, where's my tip"));
        assertTrue(AgentDialogueCatalog.tradeAllDoneReplies().contains("everything's in!"));
        assertTrue(AgentDialogueCatalog.tradeReservedForOtherReplies().contains("those are kinda spoken for, keep them safe ok?"));
        assertTrue(AgentDialogueCatalog.tradeReservedForSelfReplies().contains("heads up, I kinda wanted those for myself"));
        assertTrue(AgentDialogueCatalog.mesoReplies().contains("I have %s"));
        assertTrue(AgentDialogueCatalog.dropOrTradePrompts().contains("got %s, want me to trade or drop?"));
        assertTrue(AgentDialogueCatalog.ownerPotShortageReplies().contains("we're low on %s pots too, boss"));
        assertTrue(AgentDialogueCatalog.ammoNotNeededReplies().contains("i don't need arrows or bolts rn"));
        assertTrue(AgentDialogueCatalog.ownerAmmoShortageReplies().contains("we're low on ammo too, boss"));
        assertTrue(AgentDialogueCatalog.potRequestHpReplies().contains("need HP pots!! anyone?"));
        assertTrue(AgentDialogueCatalog.potRequestMpReplies().contains("need MP pots!! anyone?"));
        assertTrue(AgentDialogueCatalog.potOfferHpReplies().contains("got some HP pots, inv u"));
        assertTrue(AgentDialogueCatalog.potOfferMpReplies().contains("got some MP pots, inv u"));
        assertTrue(AgentDialogueCatalog.arrowRequestReplies().contains("need arrows soon, anyone got extras?"));
        assertTrue(AgentDialogueCatalog.boltRequestReplies().contains("need crossbow bolts soon, anyone got extras?"));
        assertTrue(AgentDialogueCatalog.ammoOfferReplies().contains("got some ammo for you, trading"));
        assertTrue(AgentDialogueCatalog.shopResupplyReplies().contains("one sec, going to restock"));
        assertTrue(AgentDialogueCatalog.shoppingReplies().contains("restocking now"));
        assertTrue(AgentDialogueCatalog.offerAcceptReplies().contains("yes please!"));
        assertEquals("busy rn, ask me again in a bit", AgentDialogueCatalog.offerBusyReply());
        assertEquals("nothing i need from you rn, im good!", AgentDialogueCatalog.offerNoUpgradeNeededReply());
        assertEquals("ty! inv me?", AgentDialogueCatalog.offerOwnerRequestingTradeReply());
        assertEquals("ok, keeping it for now", AgentDialogueCatalog.offerKeepItemReply());
        assertTrue(AgentDialogueCatalog.ownerUpgradeRequestPromptTemplates().contains("Can I have your %s?"));
        assertTrue(AgentDialogueCatalog.lootOfferPromptTemplates(false).contains("%s, I have %s, you want?"));
        assertTrue(AgentDialogueCatalog.lootOfferPromptTemplates(true).contains("%s, holding %s in case you want it later"));
        assertEquals("Can I have your Blue Moon?",
                AgentDialogueCatalog.formatOwnerUpgradeRequestPrompt("Can I have your %s?", "Blue Moon"));
        assertEquals("Alice, I have Blue Moon, you want?",
                AgentDialogueCatalog.formatLootOfferPrompt("%s, I have %s, you want?", "Alice", "Blue Moon"));
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
        assertEquals("cant read my movement stats rn", AgentDialogueCatalog.movementStatsUnavailableReply());
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
        assertEquals("cheap", AgentDialogueCatalog.buffConsumablesModeLabel(true));
        assertEquals("max", AgentDialogueCatalog.buffConsumablesModeLabel(false));
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

}
