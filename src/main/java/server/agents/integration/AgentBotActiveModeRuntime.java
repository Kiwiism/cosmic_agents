package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.bots.BotEntry;
import server.agents.capabilities.equipment.AgentEquipmentService;
import server.agents.capabilities.trade.AgentOfferService;
import server.agents.capabilities.supplies.AgentPotionService;

/**
 * Temporary Agent-owned bridge for active-mode side effects while equipment,
 * supply, and offer implementations still live in the bot runtime.
 */
public final class AgentBotActiveModeRuntime {
    private AgentBotActiveModeRuntime() {
    }

    public static AgentChatStatusRuntime.ActiveModeActions activeModeActions(BotEntry entry) {
        return new AgentChatStatusRuntime.ActiveModeActions() {
            @Override
            public void autoEquip() {
                AgentBotActiveModeRuntime.autoEquip(entry);
            }

            @Override
            public void resetGearSuggestionCooldown() {
                AgentBotActiveModeRuntime.resetGearSuggestionCooldown(entry);
            }

            @Override
            public void maybeSuggestGearToSiblings() {
                AgentBotActiveModeRuntime.maybeSuggestGearToSiblings(entry, entry.bot());
            }

            @Override
            public void setupAutopot() {
                AgentPotionService.setupAutopotForBot(entry.bot());
            }

            @Override
            public void checkPotShareOnModeStart() {
                AgentPotionService.checkPotShareOnModeStart(entry, entry.bot());
            }
        };
    }

    public static void autoEquipAndSuggestGearToSiblings(BotEntry entry) {
        autoEquip(entry);
        resetGearSuggestionCooldown(entry);
        maybeSuggestGearToSiblings(entry, entry.bot());
    }

    public static void maybeSuggestGearToSiblings(BotEntry entry, Character bot) {
        AgentChatStatusRuntime.maybeSuggestGear(
                AgentBotStatusRuntime.gearSuggestionState(entry),
                AgentChatStatusRuntime.gearSuggestionActions(
                        entry.owner() != null,
                        () -> AgentOfferService.offerBestGearToSibling(entry, bot)),
                System.currentTimeMillis());
    }

    private static void autoEquip(BotEntry entry) {
        AgentEquipmentService.autoEquip(entry.bot(), entry.owner(), AgentBotOfferStateRuntime.pendingLootOfferItem(entry));
    }

    private static void resetGearSuggestionCooldown(BotEntry entry) {
        AgentBotOfferStateRuntime.setNextGearSuggestionAt(entry, 0);
    }
}
