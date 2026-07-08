package server.agents.integration;


import server.agents.runtime.AgentSchedulerRuntime;
import client.Character;
import server.agents.capabilities.dialogue.AgentChatEquipmentFlow;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.equipment.AgentEquipmentService;
import server.agents.capabilities.trade.AgentOfferStateRuntime;

import java.util.List;

/**
 * Temporary Agent-owned bridge for equipment chat callbacks while equipment
 * side effects still live in the bot runtime.
 */
public final class AgentEquipmentRuntime {
    private AgentEquipmentRuntime() {
    }

    public static void sayMapNow(Character bot, String message) {
        AgentReplyRuntime.sayMapNow(bot, message);
    }

    public static AgentChatEquipmentFlow.EquipmentCallbacks equipmentCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatEquipmentFlow.EquipmentCallbacks() {
            @Override
            public boolean unequipSlot(String slotName) {
                short[] slots = AgentEquipmentService.slotsFromName(slotName);
                if (slots.length == 0) {
                    return false;
                }
                AgentSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentReplyRuntime.replyNow(entry, AgentEquipmentService.unequipSlot(bot(entry), slots)));
                return true;
            }

            @Override
            public void unequipAll() {
                AgentSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    AgentMovementCommandRuntime.stop(entry);
                    AgentReplyRuntime.replyNow(entry, AgentEquipmentService.unequipAll(bot(entry)));
                });
            }

            @Override
            public void autoEquipDebug() {
                AgentSchedulerRuntime.afterRandomDelay(400, 600, () -> {
                    List<String> lines = AgentEquipmentService.autoEquipDebug(bot(entry));
                    for (String line : lines) {
                        AgentReplyRuntime.replyNow(entry, line);
                    }
                });
            }

            @Override
            public void autoEquip() {
                AgentSchedulerRuntime.afterRandomDelay(400, 600, () -> {
                    AgentEquipmentService.autoEquip(
                            bot(entry),
                            AgentRuntimeIdentityRuntime.owner(entry),
                            AgentOfferStateRuntime.pendingLootOfferItem(entry),
                            true);
                    AgentReplyRuntime.replyNow(entry, AgentChatEquipmentFlow.gearOptimizedReply());
                });
            }
        };
    }

    private static Character bot(AgentRuntimeEntry entry) {
        return AgentRuntimeIdentityRuntime.bot(entry);
    }
}
