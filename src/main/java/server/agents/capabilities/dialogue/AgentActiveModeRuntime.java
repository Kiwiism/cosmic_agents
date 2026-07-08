package server.agents.capabilities.dialogue;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentStatusStateRuntime;
import server.agents.capabilities.equipment.AgentEquipmentService;
import server.agents.capabilities.trade.AgentOfferService;
import server.agents.capabilities.trade.AgentOfferStateRuntime;
import server.agents.capabilities.supplies.AgentPotionService;

/**
 * Agent-owned active-mode preparation callbacks over equipment, supply, and
 * offer services while live identity lookup remains an integration boundary.
 */
public final class AgentActiveModeRuntime {
    private AgentActiveModeRuntime() {
    }

    public static AgentChatStatusRuntime.ActiveModeActions activeModeActions(AgentRuntimeEntry entry) {
        return new AgentChatStatusRuntime.ActiveModeActions() {
            @Override
            public void autoEquip() {
                AgentActiveModeRuntime.autoEquip(entry);
            }

            @Override
            public void resetGearSuggestionCooldown() {
                AgentActiveModeRuntime.resetGearSuggestionCooldown(entry);
            }

            @Override
            public void maybeSuggestGearToSiblings() {
                AgentActiveModeRuntime.maybeSuggestGearToSiblings(entry, bot(entry));
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

    public static void autoEquipAndSuggestGearToSiblings(AgentRuntimeEntry entry) {
        autoEquip(entry);
        resetGearSuggestionCooldown(entry);
        maybeSuggestGearToSiblings(entry, bot(entry));
    }

    public static void maybeSuggestGearToSiblings(AgentRuntimeEntry entry, Character bot) {
        Character owner = AgentRuntimeIdentityRuntime.owner(entry);
        AgentChatStatusRuntime.maybeSuggestGear(
                AgentStatusStateRuntime.gearSuggestionState(entry),
                AgentChatStatusRuntime.gearSuggestionActions(
                        owner != null,
                        () -> AgentOfferService.offerBestGearToSibling(entry, bot)),
                System.currentTimeMillis());
    }

    private static void autoEquip(AgentRuntimeEntry entry) {
        AgentEquipmentService.autoEquip(
                bot(entry),
                AgentRuntimeIdentityRuntime.owner(entry),
                AgentOfferStateRuntime.pendingLootOfferItem(entry));
    }

    private static void resetGearSuggestionCooldown(AgentRuntimeEntry entry) {
        AgentOfferStateRuntime.setNextGearSuggestionAt(entry, 0);
    }

    private static Character bot(AgentRuntimeEntry entry) {
        return AgentRuntimeIdentityRuntime.bot(entry);
    }
}
