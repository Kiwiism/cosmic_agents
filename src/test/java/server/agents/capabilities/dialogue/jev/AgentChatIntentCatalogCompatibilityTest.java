package server.agents.capabilities.dialogue.jev;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.dialogue.AgentBuildDialogueClassifier;
import server.agents.capabilities.dialogue.AgentChatCommandClassifier;
import server.agents.capabilities.dialogue.AgentEquipmentDialogueClassifier;
import server.agents.capabilities.dialogue.AgentSocialDialogueClassifier;
import server.agents.capabilities.dialogue.AgentTradeDialogueClassifier;
import server.agents.capabilities.dialogue.AgentUtilityDialogueClassifier;
import server.agents.capabilities.movement.AgentPartyGatherService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every canonical phrase the judge can re-dispatch must be recognised by the deterministic
 * classifiers, otherwise a confident judgment would be silently ignored.
 */
class AgentChatIntentCatalogCompatibilityTest {
    private static final Map<AgentChatIntent, Predicate<String>> INTENT_PREDICATES = Map.ofEntries(
            Map.entry(AgentChatIntent.FOLLOW, AgentChatCommandClassifier::isFollowCommand),
            Map.entry(AgentChatIntent.STOP, AgentChatCommandClassifier::isStopCommand),
            Map.entry(AgentChatIntent.MOVE_HERE, AgentChatCommandClassifier::isMoveHereCommand),
            Map.entry(AgentChatIntent.GRIND, AgentChatCommandClassifier::isGrindCommand),
            Map.entry(AgentChatIntent.PATROL, AgentChatCommandClassifier::isPatrolCommand),
            Map.entry(AgentChatIntent.SENTRY, AgentChatCommandClassifier::isFarmHereCommand),
            Map.entry(AgentChatIntent.FIDGET, AgentChatCommandClassifier::isFidgetCommand),
            Map.entry(AgentChatIntent.FAME, message -> AgentSocialDialogueClassifier.matchFameTarget(message) != null),
            Map.entry(AgentChatIntent.GEAR_FOR_SPEAKER, AgentChatCommandClassifier::isRecommendedGearQuery),
            Map.entry(AgentChatIntent.GEAR_FOR_BOT, AgentChatCommandClassifier::isRequestUpgradeCommand),
            Map.entry(AgentChatIntent.TRADE_WINDOW, AgentUtilityDialogueClassifier::isTradeInviteCommand),
            Map.entry(AgentChatIntent.SELL_TRASH, AgentUtilityDialogueClassifier::isSellTrashCommand),
            Map.entry(AgentChatIntent.AUTOEQUIP, AgentEquipmentDialogueClassifier::isAutoEquipCommand),
            Map.entry(AgentChatIntent.UNEQUIP_ALL, AgentEquipmentDialogueClassifier::isUnequipAllCommand),
            Map.entry(AgentChatIntent.CHANGE_BUILD, AgentBuildDialogueClassifier::isApChangeBuildCommand),
            Map.entry(AgentChatIntent.HELP, AgentChatCommandClassifier::isHelpCommand),
            Map.entry(AgentChatIntent.RELOG, AgentChatCommandClassifier::isRelogRequest),
            Map.entry(AgentChatIntent.LOGOUT, AgentChatCommandClassifier::isLogoutRequest));

    private static final Map<String, Predicate<String>> INFO_PREDICATES = Map.ofEntries(
            Map.entry("stats", AgentChatCommandClassifier::isStatsQuery),
            Map.entry("inventory", AgentChatCommandClassifier::isInventoryQuery),
            Map.entry("slots", AgentChatCommandClassifier::isInventorySlotsQuery),
            Map.entry("range", AgentChatCommandClassifier::isRangeQuery),
            Map.entry("build", AgentChatCommandClassifier::isBuildQuery),
            Map.entry("skills", AgentChatCommandClassifier::isSkillsQuery),
            Map.entry("mesos", AgentChatCommandClassifier::isMesoQuery),
            Map.entry("speed", AgentChatCommandClassifier::isMovementStatsQuery),
            Map.entry("exp", AgentChatCommandClassifier::isExpQuery),
            Map.entry("scrolls", AgentChatCommandClassifier::isScrollsQuery),
            Map.entry("pots", AgentChatCommandClassifier::isPotionsQuery),
            Map.entry("buffs", AgentChatCommandClassifier::isBuffListQuery));

    private static final Map<String, Predicate<String>> SUPPORT_PREDICATES = Map.ofEntries(
            Map.entry("support_on", AgentChatCommandClassifier::isSupportOnCommand),
            Map.entry("support_off", AgentChatCommandClassifier::isSupportOffCommand),
            Map.entry("heals_on", AgentChatCommandClassifier::isHealsOnCommand),
            Map.entry("heals_off", AgentChatCommandClassifier::isHealsOffCommand),
            Map.entry("buff_pots_on", AgentChatCommandClassifier::isBuffConsumablesOnCommand),
            Map.entry("buff_pots_off", AgentChatCommandClassifier::isBuffConsumablesOffCommand),
            Map.entry("buff_pots_cheap", AgentChatCommandClassifier::isBuffConsumablesCheapCommand),
            Map.entry("buff_pots_max", AgentChatCommandClassifier::isBuffConsumablesMaxCommand));

    private static final Map<String, Predicate<String>> GATHER_PREDICATES = Map.of(
            "gather", AgentPartyGatherService::matchesAssemble,
            "buffs", message -> AgentPartyGatherService.matchesAssemble(message)
                    && AgentPartyGatherService.mentionsBuffs(message));

    private static final Map<String, Predicate<String>> SUPPLY_PREDICATES = Map.of(
            "hp", AgentChatCommandClassifier::isNeedHpPotCommand,
            "mp", AgentChatCommandClassifier::isNeedMpPotCommand,
            "ammo", AgentChatCommandClassifier::isNeedAmmoCommand,
            "any", AgentChatCommandClassifier::isNeedPotCommand);

    @Test
    void everyArgumentFreeIntentHasARecognisedCanonicalPhrase() {
        List<String> failures = new ArrayList<>();
        for (AgentChatIntent intent : AgentChatIntent.values()) {
            if (!intent.actionable() || intent.argument() != null) {
                continue;
            }
            assertNotNull(intent.canonicalCommand(), intent + " needs a canonical phrase");
            Predicate<String> predicate = INTENT_PREDICATES.get(intent);
            assertNotNull(predicate, intent + " has no compatibility predicate in this test");
            if (!predicate.test(intent.canonicalCommand())) {
                failures.add(intent + " -> '" + intent.canonicalCommand() + "'");
            }
        }
        assertEquals(List.of(), failures, "canonical phrases not recognised by the classifiers");
    }

    @Test
    void everyArgumentOptionProducesARecognisedPhrase() {
        List<String> failures = new ArrayList<>();
        check(failures, AgentChatIntent.INFO_QUERY, AgentChatIntentArgument.INFO_TOPIC, INFO_PREDICATES);
        check(failures, AgentChatIntent.SUPPORT_TOGGLE, AgentChatIntentArgument.SUPPORT_TARGET, SUPPORT_PREDICATES);
        check(failures, AgentChatIntent.SUPPLY_REQUEST, AgentChatIntentArgument.SUPPLY_KIND, SUPPLY_PREDICATES);
        check(failures, AgentChatIntent.ASSEMBLE, AgentChatIntentArgument.GATHER_PURPOSE, GATHER_PREDICATES);
        for (String option : AgentChatIntentArgument.ITEM_CATEGORY.criteria().keySet()) {
            String trade = canonical(AgentChatIntent.TRADE_ITEMS, AgentChatIntentArgument.ITEM_CATEGORY, option);
            if (AgentTradeDialogueClassifier.matchTradeCategory(trade) == null) {
                failures.add("TRADE_ITEMS/" + option + " -> '" + trade + "'");
            }
            String drop = canonical(AgentChatIntent.DROP_ITEMS, AgentChatIntentArgument.ITEM_CATEGORY, option);
            if (AgentTradeDialogueClassifier.matchChoiceCategory(drop) == null) {
                failures.add("DROP_ITEMS/" + option + " -> '" + drop + "'");
            }
        }
        for (String option : AgentChatIntentArgument.EQUIP_SLOT.criteria().keySet()) {
            String unequip = canonical(AgentChatIntent.UNEQUIP_SLOT, AgentChatIntentArgument.EQUIP_SLOT, option);
            if (AgentEquipmentDialogueClassifier.matchUnequipSlotName(unequip) == null) {
                failures.add("UNEQUIP_SLOT/" + option + " -> '" + unequip + "'");
            }
        }
        assertTrue(AgentChatCommandClassifier.isRespecCommand(
                canonical(AgentChatIntent.RESPEC, AgentChatIntentArgument.RESPEC_KIND, "sp")));
        assertTrue(AgentChatCommandClassifier.isApRespecCommand(
                canonical(AgentChatIntent.RESPEC, AgentChatIntentArgument.RESPEC_KIND, "ap")));
        assertEquals(List.of(), failures, "argument phrases not recognised by the classifiers");
    }

    private static void check(List<String> failures, AgentChatIntent intent, AgentChatIntentArgument argument,
                              Map<String, Predicate<String>> predicates) {
        for (String option : argument.criteria().keySet()) {
            Predicate<String> predicate = predicates.get(option);
            assertNotNull(predicate, argument + "/" + option + " has no compatibility predicate in this test");
            String phrase = canonical(intent, argument, option);
            if (!predicate.test(phrase)) {
                failures.add(intent + "/" + option + " -> '" + phrase + "'");
            }
        }
    }

    private static String canonical(AgentChatIntent intent, AgentChatIntentArgument argument, String option) {
        return AgentChatIntentCatalog.canonicalCommand(intent, argument.phraseFor(option));
    }
}
