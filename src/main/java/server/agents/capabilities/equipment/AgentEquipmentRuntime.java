package server.agents.capabilities.equipment;


import server.agents.runtime.AgentSchedulerRuntime;
import client.Character;
import server.agents.capabilities.dialogue.AgentChatEquipmentFlow;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.integration.AgentDialogueTransportRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.trade.AgentOfferStateRuntime;

import java.util.List;

/**
 * Agent-owned bridge for equipment chat callbacks while reply delivery and
 * movement stop side effects stay behind integration runtime boundaries.
 */
public final class AgentEquipmentRuntime {
    private AgentEquipmentRuntime() {
    }

    public static void sayMapNow(Character bot, String message) {
        AgentDialogueTransportRuntime.sayMapNow(bot, message);
    }

    public static AgentChatEquipmentFlow.EquipmentCallbacks equipmentCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatEquipmentFlow.EquipmentCallbacks() {
            @Override
            public boolean unequipSlot(String slotName) {
                short[] slots = AgentEquipmentService.slotsFromName(slotName);
                if (slots.length == 0) {
                    return false;
                }
                AgentSchedulerRuntime.afterRandomDelay(entry, 500, 700, () ->
                        AgentDialogueTransportRuntime.replyNow(entry, AgentEquipmentService.unequipSlot(bot(entry), slots)));
                return true;
            }

            @Override
            public void unequipAll() {
                AgentSchedulerRuntime.afterRandomDelay(entry, 500, 700, () -> {
                    AgentMovementCommandRuntime.stop(entry);
                    AgentDialogueTransportRuntime.replyNow(entry, AgentEquipmentService.unequipAll(bot(entry)));
                });
            }

            @Override
            public void autoEquipDebug() {
                AgentSchedulerRuntime.afterRandomDelay(entry, 400, 600, () -> {
                    List<String> lines = AgentEquipmentService.autoEquipDebug(bot(entry));
                    for (String line : lines) {
                        AgentDialogueTransportRuntime.replyNow(entry, line);
                    }
                });
            }

            @Override
            public void autoEquip() {
                AgentSchedulerRuntime.afterRandomDelay(entry, 400, 600, () -> {
                    AgentEquipmentService.autoEquip(
                            bot(entry),
                            null,
                            AgentOfferStateRuntime.pendingLootOfferItem(entry),
                            true);
                    AgentDialogueTransportRuntime.replyNow(entry, AgentChatEquipmentFlow.gearOptimizedReply());
                });
            }
        };
    }

    private static Character bot(AgentRuntimeEntry entry) {
        return AgentRuntimeIdentityRuntime.bot(entry);
    }
}
