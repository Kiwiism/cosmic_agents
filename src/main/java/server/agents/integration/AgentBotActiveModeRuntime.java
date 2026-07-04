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
                AgentBotActiveModeRuntime.maybeSuggestGearToSiblings(entry, bot(entry));
            }

            @Override
            public void setupAutopot() {
                AgentPotionService.setupAutopotForBot(bot(entry));
            }

            @Override
            public void checkPotShareOnModeStart() {
                AgentPotionService.checkPotShareOnModeStart(entry, bot(entry));
            }
        };
    }

    public static void autoEquipAndSuggestGearToSiblings(BotEntry entry) {
        autoEquip(entry);
        resetGearSuggestionCooldown(entry);
        maybeSuggestGearToSiblings(entry, bot(entry));
    }

    public static void maybeSuggestGearToSiblings(BotEntry entry, Character bot) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        AgentChatStatusRuntime.maybeSuggestGear(
                AgentBotStatusRuntime.gearSuggestionState(entry),
                AgentChatStatusRuntime.gearSuggestionActions(
                        owner != null,
                        () -> AgentOfferService.offerBestGearToSibling(entry, bot)),
                System.currentTimeMillis());
    }

    private static void autoEquip(BotEntry entry) {
        AgentEquipmentService.autoEquip(
                bot(entry),
                AgentBotRuntimeIdentityRuntime.owner(entry),
                AgentBotOfferStateRuntime.pendingLootOfferItem(entry));
    }

    private static void resetGearSuggestionCooldown(BotEntry entry) {
        AgentBotOfferStateRuntime.setNextGearSuggestionAt(entry, 0);
    }

    private static Character bot(BotEntry entry) {
        return AgentBotRuntimeIdentityRuntime.bot(entry);
    }
}
