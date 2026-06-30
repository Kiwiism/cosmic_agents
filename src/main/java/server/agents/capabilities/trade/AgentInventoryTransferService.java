package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import server.bots.BotEntry;
import server.bots.BotInventoryManager;

/**
 * Agent-owned boundary for inventory transfer commands while the deeper trade
 * state machine is still being reconstructed out of BotInventoryManager.
 */
public final class AgentInventoryTransferService {
    private AgentInventoryTransferService() {
    }

    public static void executeChoice(String category, boolean tradeToOwner, BotEntry entry, Character agent) {
        BotInventoryManager.executeChoice(category, tradeToOwner, entry, agent);
    }

    public static void startTradeTransfer(String category, BotEntry entry, Character agent) {
        BotInventoryManager.startTradeTransfer(category, entry, agent);
    }

    public static void startTradeTransfer(Item item, Character recipient, BotEntry entry, Character agent) {
        BotInventoryManager.startTradeTransfer(item, recipient, entry, agent);
    }

    public static boolean hasTransferableItems(String category, BotEntry entry, Character agent) {
        return BotInventoryManager.hasTransferableItems(category, entry, agent);
    }

    public static int countTransferableItems(String category, BotEntry entry, Character agent) {
        return BotInventoryManager.countTransferableItems(category, entry, agent);
    }
}
