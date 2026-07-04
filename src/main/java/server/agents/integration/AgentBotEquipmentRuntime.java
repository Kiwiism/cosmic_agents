package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatEquipmentFlow;
import server.bots.BotEntry;
import server.agents.capabilities.equipment.AgentEquipmentService;

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
                short[] slots = AgentEquipmentService.slotsFromName(slotName);
                if (slots.length == 0) {
                    return false;
                }
                AgentBotEquipmentSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentBotEquipmentReplyRuntime.replyNow(entry, AgentEquipmentService.unequipSlot(bot(entry), slots)));
                return true;
            }

            @Override
            public void unequipAll() {
                AgentBotEquipmentSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    AgentBotMovementCommandRuntime.stop(entry);
                    AgentBotEquipmentReplyRuntime.replyNow(entry, AgentEquipmentService.unequipAll(bot(entry)));
                });
            }

            @Override
            public void autoEquipDebug() {
                AgentBotEquipmentSchedulerRuntime.afterRandomDelay(400, 600, () -> {
                    List<String> lines = AgentEquipmentService.autoEquipDebug(bot(entry));
                    for (String line : lines) {
                        AgentBotEquipmentReplyRuntime.replyNow(entry, line);
                    }
                });
            }

            @Override
            public void autoEquip() {
                AgentBotEquipmentSchedulerRuntime.afterRandomDelay(400, 600, () -> {
                    AgentEquipmentService.autoEquip(
                            bot(entry),
                            AgentBotRuntimeIdentityRuntime.owner(entry),
                            AgentBotOfferStateRuntime.pendingLootOfferItem(entry),
                            true);
                    AgentBotEquipmentReplyRuntime.replyNow(entry, AgentChatEquipmentFlow.gearOptimizedReply());
                });
            }
        };
    }

    private static Character bot(BotEntry entry) {
        return AgentBotRuntimeIdentityRuntime.bot(entry);
    }
}
