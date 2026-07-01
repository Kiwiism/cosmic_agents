package server.bots;

import client.Character;
import client.inventory.Item;
import server.agents.capabilities.looting.AgentPassiveLootRuntimeService;
import server.agents.capabilities.trade.AgentInventoryTransferService;
import server.agents.capabilities.trade.AgentManualTradeRuntimeService;
import server.agents.capabilities.trade.AgentTradeLifecycleRuntimeService;
import server.agents.capabilities.trade.AgentTradeTickRuntimeService;
import server.agents.capabilities.trade.AgentTradeTransferAvailabilityRuntimeService;
import server.agents.integration.AgentBotInventoryRuntimeAdapters;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;

public class BotInventoryManager {
    static void tickPassiveLoot(BotEntry entry, Character bot) {
        AgentPassiveLootRuntimeService.tickPassiveLoot(
                entry,
                bot,
                AgentBotInventoryRuntimeAdapters.passiveLootRuntimeCallbacks());
    }

    static void tickManualTrade(BotEntry entry, Character bot) {
        AgentManualTradeRuntimeService.tickManualTrade(
                entry,
                bot,
                AgentBotRuntimeIdentityRuntime.owner(entry),
                AgentBotInventoryRuntimeAdapters.manualTradeRuntimeCallbacks(entry),
                AgentTradeLifecycleRuntimeService.lifecycleCallbacks(
                        AgentBotInventoryRuntimeAdapters.tradeLifecycleRuntimeCallbacks()));
    }

    public static void executeChoice(String category, boolean tradeToOwner, BotEntry entry, Character bot) {
        AgentInventoryTransferService.executeChoice(category, tradeToOwner, entry, bot);
    }

    public static void startTradeTransfer(String category, BotEntry entry, Character bot) {
        AgentInventoryTransferService.startTradeTransfer(category, entry, bot);
    }

    public static void startTradeTransfer(Item item, Character recipient, BotEntry entry, Character bot) {
        AgentInventoryTransferService.startTradeTransfer(item, recipient, entry, bot);
    }

    public static boolean hasTransferableItems(String category, BotEntry entry, Character bot) {
        return AgentTradeTransferAvailabilityRuntimeService.hasTransferableItems(
                category,
                entry,
                bot,
                AgentBotInventoryRuntimeAdapters.transferAvailabilityRuntimeCallbacks(),
                AgentBotInventoryRuntimeAdapters.tradeRuntimeCallbacks(entry, bot));
    }

    public static int countTransferableItems(String category, BotEntry entry, Character bot) {
        return AgentTradeTransferAvailabilityRuntimeService.countTransferableItems(
                category,
                entry,
                bot,
                AgentBotInventoryRuntimeAdapters.transferAvailabilityRuntimeCallbacks(),
                AgentBotInventoryRuntimeAdapters.tradeRuntimeCallbacks(entry, bot));
    }

    static void tickTrade(BotEntry entry, Character bot) {
        AgentTradeTickRuntimeService.tickTrade(
                entry,
                bot,
                AgentBotInventoryRuntimeAdapters.tradeTickRuntimeCallbacks(),
                AgentBotInventoryRuntimeAdapters.tradeRuntimeCallbacks(entry, bot),
                AgentBotInventoryRuntimeAdapters.tradeLifecycleRuntimeCallbacks());
    }
}
