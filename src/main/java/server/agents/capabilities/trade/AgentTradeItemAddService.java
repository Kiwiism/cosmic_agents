package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import net.packet.Packet;
import server.Trade;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.bots.BotEntry;
import tools.PacketCreator;

import java.util.List;

public final class AgentTradeItemAddService {
    private AgentTradeItemAddService() {
    }

    public static boolean addNextItem(BotEntry entry, Character agent, Trade trade, int delayMs) {
        return addNextItem(
                entry,
                agent,
                trade,
                delayMs,
                (character, type, slot, quantity, fromDrop) ->
                        InventoryManipulator.removeFromSlot(character.getClient(), type, slot, quantity, fromDrop),
                PacketCreator::getTradeItemAdd);
    }

    static boolean addNextItem(BotEntry entry,
                               Character agent,
                               Trade trade,
                               int delayMs,
                               InventoryRemover inventoryRemover,
                               TradeItemPacketFactory packetFactory) {
        List<Item> items = AgentBotPendingTradeStateRuntime.items(entry);
        int idx = AgentBotPendingTradeStateRuntime.itemIndex(entry);
        if (idx >= items.size()) {
            return false;
        }

        Item item = items.get(idx);
        AgentBotPendingTradeStateRuntime.incrementItemIndex(entry);
        AgentBotPendingTradeStateRuntime.setTimerMs(entry, delayMs);

        short tradeQty = AgentBotPendingTradeStateRuntime.capShareQuantity(entry, item.getQuantity());
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
                AgentBotPendingTradeStateRuntime.transferRestoreSlot(entry, item, tradeItem);
                inventoryRemover.remove(agent, invType, item.getPosition(), tradeQty, false);
                agent.sendPacket(packetFactory.create((byte) 0, tradeItem));
                if (trade.getPartner() != null) {
                    trade.getPartner().getChr().sendPacket(packetFactory.create((byte) 1, tradeItem));
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
    interface TradeItemPacketFactory {
        Packet create(byte number, Item item);
    }
}
