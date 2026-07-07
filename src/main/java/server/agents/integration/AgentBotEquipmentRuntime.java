package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatEquipmentFlow;
import server.agents.runtime.AgentRuntimeEntry;
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
        AgentBotReplyRuntime.sayMapNow(bot, message);
    }

    public static AgentChatEquipmentFlow.EquipmentCallbacks equipmentCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatEquipmentFlow.EquipmentCallbacks() {
            @Override
            public boolean unequipSlot(String slotName) {
                short[] slots = AgentEquipmentService.slotsFromName(slotName);
                if (slots.length == 0) {
                    return false;
                }
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentBotReplyRuntime.replyNow(entry, AgentEquipmentService.unequipSlot(bot(entry), slots)));
                return true;
            }

            @Override
            public void unequipAll() {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    AgentBotMovementCommandRuntime.stop(entry);
                    AgentBotReplyRuntime.replyNow(entry, AgentEquipmentService.unequipAll(bot(entry)));
                });
            }

            @Override
            public void autoEquipDebug() {
                AgentBotSchedulerRuntime.afterRandomDelay(400, 600, () -> {
                    List<String> lines = AgentEquipmentService.autoEquipDebug(bot(entry));
                    for (String line : lines) {
                        AgentBotReplyRuntime.replyNow(entry, line);
                    }
                });
            }

            @Override
            public void autoEquip() {
                AgentBotSchedulerRuntime.afterRandomDelay(400, 600, () -> {
                    AgentEquipmentService.autoEquip(
                            bot(entry),
                            AgentBotRuntimeIdentityRuntime.owner(entry),
                            AgentBotOfferStateRuntime.pendingLootOfferItem(entry),
                            true);
                    AgentBotReplyRuntime.replyNow(entry, AgentChatEquipmentFlow.gearOptimizedReply());
                });
            }
        };
    }

    private static Character bot(AgentRuntimeEntry entry) {
        return AgentBotRuntimeIdentityRuntime.bot(entry);
    }
}
