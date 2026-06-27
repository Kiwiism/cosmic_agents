package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.bots.BotEntry;
import server.bots.BotEquipManager;
import server.bots.BotOfferManager;
import server.bots.BotPotionManager;

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
                BotPotionManager.setupAutopotForBot(entry.bot());
            }

            @Override
            public void checkPotShareOnModeStart() {
                BotPotionManager.checkPotShareOnModeStart(entry, entry.bot());
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
                        () -> BotOfferManager.offerBestGearToSibling(entry, bot)),
                System.currentTimeMillis());
    }

    private static void autoEquip(BotEntry entry) {
        BotEquipManager.autoEquip(entry.bot(), entry.owner(), entry.pendingLootOfferItem());
    }

    private static void resetGearSuggestionCooldown(BotEntry entry) {
        entry.setNextGearSuggestionAt(0);
    }
}
