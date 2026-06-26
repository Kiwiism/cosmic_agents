package server.bots;

import server.agents.capabilities.dialogue.AgentChatEquipmentFlow;

import java.util.List;

final class BotChatEquipmentRuntime {
    private BotChatEquipmentRuntime() {
    }

    static AgentChatEquipmentFlow.EquipmentCallbacks equipmentCallbacks(BotEntry entry) {
        return new AgentChatEquipmentFlow.EquipmentCallbacks() {
            @Override
            public boolean unequipSlot(String slotName) {
                short[] slots = BotEquipManager.slotsFromName(slotName);
                if (slots.length == 0) {
                    return false;
                }
                BotManager.after(BotManager.randMs(500, 700), () ->
                        BotManager.getInstance().botReply(entry, BotEquipManager.unequipSlot(entry.bot, slots)));
                return true;
            }

            @Override
            public void unequipAll() {
                BotManager.after(BotManager.randMs(500, 700), () -> {
                    BotManager.getInstance().issueStop(entry);
                    BotManager.getInstance().botReply(entry, BotEquipManager.unequipAll(entry.bot));
                });
            }

            @Override
            public void autoEquipDebug() {
                BotManager.after(BotManager.randMs(400, 600), () -> {
                    List<String> lines = BotEquipManager.autoEquipDebug(entry.bot);
                    for (String line : lines) {
                        BotManager.getInstance().botReply(entry, line);
                    }
                });
            }

            @Override
            public void autoEquip() {
                BotManager.after(BotManager.randMs(400, 600), () -> {
                    BotEquipManager.autoEquip(entry.bot, entry.owner, entry.pendingLootOfferItem, true);
                    BotManager.getInstance().botReply(entry, AgentChatEquipmentFlow.gearOptimizedReply());
                });
            }
        };
    }
}
