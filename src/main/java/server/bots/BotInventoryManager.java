package server.bots;

import client.Character;
import client.inventory.Item;
import server.agents.capabilities.inventory.AgentInventoryTickRuntime;

public class BotInventoryManager {
    public static void tickPassiveLoot(BotEntry entry, Character bot) {
        AgentInventoryTickRuntime.tickPassiveLoot(entry, bot);
    }

    public static void tickManualTrade(BotEntry entry, Character bot) {
        AgentInventoryTickRuntime.tickManualTrade(entry, bot);
    }

    public static void executeChoice(String category, boolean tradeToOwner, BotEntry entry, Character bot) {
        AgentInventoryTickRuntime.executeChoice(category, tradeToOwner, entry, bot);
    }

    public static void startTradeTransfer(String category, BotEntry entry, Character bot) {
        AgentInventoryTickRuntime.startTradeTransfer(category, entry, bot);
    }

    public static void startTradeTransfer(Item item, Character recipient, BotEntry entry, Character bot) {
        AgentInventoryTickRuntime.startTradeTransfer(item, recipient, entry, bot);
    }

    public static boolean hasTransferableItems(String category, BotEntry entry, Character bot) {
        return AgentInventoryTickRuntime.hasTransferableItems(category, entry, bot);
    }

    public static int countTransferableItems(String category, BotEntry entry, Character bot) {
        return AgentInventoryTickRuntime.countTransferableItems(category, entry, bot);
    }

    public static void tickTrade(BotEntry entry, Character bot) {
        AgentInventoryTickRuntime.tickTrade(entry, bot);
    }
}
