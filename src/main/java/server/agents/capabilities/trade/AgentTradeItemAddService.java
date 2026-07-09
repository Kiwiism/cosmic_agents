package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.AgentPacketGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

public final class AgentTradeItemAddService {
    private AgentTradeItemAddService() {
    }

    public static boolean addNextItem(AgentRuntimeEntry entry, Character agent, AgentTradeWindow trade, int delayMs) {
        return addNextItem(
                entry,
                agent,
                trade,
                delayMs,
                (character, type, slot, quantity, fromDrop) ->
                        AgentInventoryGatewayRuntime.inventory().removeFromSlot(character, type, slot, quantity, fromDrop),
                (recipient, number, item) ->
                        AgentPacketGatewayRuntime.packets().sendTradeItemAdd(recipient, number, item));
    }

    static boolean addNextItem(AgentRuntimeEntry entry,
                               Character agent,
                               AgentTradeWindow trade,
                               int delayMs,
                               InventoryRemover inventoryRemover,
                               TradeItemPacketSender packetSender) {
        List<Item> items = AgentPendingTradeStateRuntime.items(entry);
        int idx = AgentPendingTradeStateRuntime.itemIndex(entry);
        if (idx >= items.size()) {
            return false;
        }

        Item item = items.get(idx);
        AgentPendingTradeStateRuntime.incrementItemIndex(entry);
        AgentPendingTradeStateRuntime.setTimerMs(entry, delayMs);

        short tradeQty = AgentPendingTradeStateRuntime.capShareQuantity(entry, item.getQuantity());
        InventoryType invType = item.getInventoryType();
        Inventory inv = agent.getInventory(invType);
        inv.lockInventory();
        try {
            Item current = inv.getItem(item.getPosition());
            if (current == null || current != item) {
                return true;
            }

            Item tradeItem = item.copy();
            tradeItem.setPosition((short) (idx + 1));
            tradeItem.setQuantity(tradeQty);

            if (trade.addItem(tradeItem)) {
                AgentPendingTradeStateRuntime.transferRestoreSlot(entry, item, tradeItem);
                inventoryRemover.remove(agent, invType, item.getPosition(), tradeQty, false);
                packetSender.send(agent, (byte) 0, tradeItem);
                if (trade.partner() != null) {
                    packetSender.send(trade.partner().character(), (byte) 1, tradeItem);
                }
            }
            return true;
        } finally {
            inv.unlockInventory();
        }
    }

    @FunctionalInterface
    interface InventoryRemover {
        void remove(Character agent, InventoryType type, short slot, short quantity, boolean fromDrop);
    }

    @FunctionalInterface
    interface TradeItemPacketSender {
        void send(Character recipient, byte number, Item item);
    }
}
