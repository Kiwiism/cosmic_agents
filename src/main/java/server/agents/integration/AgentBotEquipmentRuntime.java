package server.agents.integration;

import client.Character;
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

    public static void sayMapNow(Character bot, String message) {
        AgentBotEquipmentReplyRuntime.sayMapNow(bot, message);
    }

    public static AgentChatEquipmentFlow.EquipmentCallbacks equipmentCallbacks(BotEntry entry) {
        return new AgentChatEquipmentFlow.EquipmentCallbacks() {
            @Override
            public boolean unequipSlot(String slotName) {
                short[] slots = BotEquipManager.slotsFromName(slotName);
                if (slots.length == 0) {
                    return false;
                }
                AgentBotEquipmentSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentBotEquipmentReplyRuntime.replyNow(entry, BotEquipManager.unequipSlot(entry.bot(), slots)));
                return true;
            }

            @Override
            public void unequipAll() {
                AgentBotEquipmentSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    BotManager.getInstance().issueStop(entry);
                    AgentBotEquipmentReplyRuntime.replyNow(entry, BotEquipManager.unequipAll(entry.bot()));
                });
            }

            @Override
            public void autoEquipDebug() {
                AgentBotEquipmentSchedulerRuntime.afterRandomDelay(400, 600, () -> {
                    List<String> lines = BotEquipManager.autoEquipDebug(entry.bot());
                    for (String line : lines) {
                        AgentBotEquipmentReplyRuntime.replyNow(entry, line);
                    }
                });
            }

            @Override
            public void autoEquip() {
                AgentBotEquipmentSchedulerRuntime.afterRandomDelay(400, 600, () -> {
                    BotEquipManager.autoEquip(entry.bot(), entry.owner(), AgentBotOfferStateRuntime.pendingLootOfferItem(entry), true);
                    AgentBotEquipmentReplyRuntime.replyNow(entry, AgentChatEquipmentFlow.gearOptimizedReply());
                });
            }
        };
    }
}
