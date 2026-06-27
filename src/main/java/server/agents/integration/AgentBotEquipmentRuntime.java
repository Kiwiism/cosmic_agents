package server.agents.integration;

import server.agents.capabilities.dialogue.AgentChatEquipmentFlow;
import server.bots.BotEntry;
import server.bots.BotEquipManager;
import server.bots.BotManager;

import java.util.List;

/**
 * Temporary Agent-owned bridge for equipment chat callbacks while equipment
 * side effects still live in the bot runtime.
 */
public final class AgentBotEquipmentRuntime {
    private AgentBotEquipmentRuntime() {
    }

    public static AgentChatEquipmentFlow.EquipmentCallbacks equipmentCallbacks(BotEntry entry) {
        return new AgentChatEquipmentFlow.EquipmentCallbacks() {
            @Override
            public boolean unequipSlot(String slotName) {
                short[] slots = BotEquipManager.slotsFromName(slotName);
                if (slots.length == 0) {
                    return false;
                }
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        BotManager.getInstance().botReply(entry, BotEquipManager.unequipSlot(entry.bot(), slots)));
                return true;
            }

            @Override
            public void unequipAll() {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    BotManager.getInstance().issueStop(entry);
                    BotManager.getInstance().botReply(entry, BotEquipManager.unequipAll(entry.bot()));
                });
            }

            @Override
            public void autoEquipDebug() {
                AgentBotSchedulerRuntime.afterRandomDelay(400, 600, () -> {
                    List<String> lines = BotEquipManager.autoEquipDebug(entry.bot());
                    for (String line : lines) {
                        BotManager.getInstance().botReply(entry, line);
                    }
                });
            }

            @Override
            public void autoEquip() {
                AgentBotSchedulerRuntime.afterRandomDelay(400, 600, () -> {
                    BotEquipManager.autoEquip(entry.bot(), entry.owner(), entry.pendingLootOfferItem(), true);
                    BotManager.getInstance().botReply(entry, AgentChatEquipmentFlow.gearOptimizedReply());
                });
            }
        };
    }
}
