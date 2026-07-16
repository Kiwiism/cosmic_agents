package server.agents.capabilities.equipment;


import server.agents.runtime.AgentSchedulerRuntime;
import client.Character;
import server.agents.capabilities.dialogue.AgentChatEquipmentFlow;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.trade.AgentOfferStateRuntime;

import java.util.List;

/**
 * Agent-owned bridge for equipment chat callbacks while reply delivery and
 * movement stop side effects stay behind integration runtime boundaries.
 */
public final class AgentEquipmentRuntime {
    private static final String PLAYER_MANAGED_REPLY =
            "I leave equipment decisions to you while we're adventuring partners.";

    private AgentEquipmentRuntime() {
    }

    public static void sayMapNow(Character bot, String message) {
        AgentReplyRuntime.sayMapNow(bot, message);
    }

    public static AgentChatEquipmentFlow.EquipmentCallbacks equipmentCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatEquipmentFlow.EquipmentCallbacks() {
            @Override
            public boolean unequipSlot(String slotName) {
                if (entry.isPartnerManaged()) {
                    AgentReplyRuntime.replyNow(entry, PLAYER_MANAGED_REPLY);
                    return true;
                }
                short[] slots = AgentEquipmentService.slotsFromName(slotName);
                if (slots.length == 0) {
                    return false;
                }
                AgentSchedulerRuntime.afterRandomDelay(entry, 500, 700, () -> {
                    if (entry.isPartnerManaged()) return;
                    AgentReplyRuntime.replyNow(entry, AgentEquipmentService.unequipSlot(bot(entry), slots));
                });
                return true;
            }

            @Override
            public void unequipAll() {
                if (entry.isPartnerManaged()) {
                    AgentReplyRuntime.replyNow(entry, PLAYER_MANAGED_REPLY);
                    return;
                }
                AgentSchedulerRuntime.afterRandomDelay(entry, 500, 700, () -> {
                    if (entry.isPartnerManaged()) return;
                    AgentMovementCommandRuntime.stop(entry);
                    AgentReplyRuntime.replyNow(entry, AgentEquipmentService.unequipAll(bot(entry)));
                });
            }

            @Override
            public void autoEquipDebug() {
                AgentSchedulerRuntime.afterRandomDelay(entry, 400, 600, () -> {
                    List<String> lines = AgentEquipmentService.autoEquipDebug(bot(entry));
                    for (String line : lines) {
                        AgentReplyRuntime.replyNow(entry, line);
                    }
                });
            }

            @Override
            public void autoEquip() {
                if (entry.isPartnerManaged()) {
                    AgentReplyRuntime.replyNow(entry, PLAYER_MANAGED_REPLY);
                    return;
                }
                AgentSchedulerRuntime.afterRandomDelay(entry, 400, 600, () -> {
                    if (entry.isPartnerManaged()) return;
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
