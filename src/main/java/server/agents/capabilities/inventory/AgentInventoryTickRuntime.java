package server.agents.capabilities.inventory;

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
import server.bots.BotEntry;

/**
 * Agent-owned entry points for inventory, loot, and trade tick behavior.
 */
public final class AgentInventoryTickRuntime {
    private AgentInventoryTickRuntime() {
    }

    public static void tickPassiveLoot(BotEntry entry, Character agent) {
        AgentPassiveLootRuntimeService.tickPassiveLoot(
                entry,
                agent,
                AgentBotInventoryRuntimeAdapters.passiveLootRuntimeCallbacks());
    }

    public static void tickManualTrade(BotEntry entry, Character agent) {
        AgentManualTradeRuntimeService.tickManualTrade(
                entry,
                agent,
                AgentBotRuntimeIdentityRuntime.owner(entry),
                AgentBotInventoryRuntimeAdapters.manualTradeRuntimeCallbacks(entry),
                AgentTradeLifecycleRuntimeService.lifecycleCallbacks(
                        AgentBotInventoryRuntimeAdapters.tradeLifecycleRuntimeCallbacks()));
    }

    public static void executeChoice(String category, boolean tradeToLeader, BotEntry entry, Character agent) {
        AgentInventoryTransferService.executeChoice(category, tradeToLeader, entry, agent);
    }

    public static void startTradeTransfer(String category, BotEntry entry, Character agent) {
        AgentInventoryTransferService.startTradeTransfer(category, entry, agent);
    }

    public static void startTradeTransfer(Item item, Character recipient, BotEntry entry, Character agent) {
        AgentInventoryTransferService.startTradeTransfer(item, recipient, entry, agent);
    }

    public static boolean hasTransferableItems(String category, BotEntry entry, Character agent) {
        return AgentTradeTransferAvailabilityRuntimeService.hasTransferableItems(
                category,
                entry,
                agent,
                AgentBotInventoryRuntimeAdapters.transferAvailabilityRuntimeCallbacks(),
                AgentBotInventoryRuntimeAdapters.tradeRuntimeCallbacks(entry, agent));
    }

    public static int countTransferableItems(String category, BotEntry entry, Character agent) {
        return AgentTradeTransferAvailabilityRuntimeService.countTransferableItems(
                category,
                entry,
                agent,
                AgentBotInventoryRuntimeAdapters.transferAvailabilityRuntimeCallbacks(),
                AgentBotInventoryRuntimeAdapters.tradeRuntimeCallbacks(entry, agent));
    }

    public static void tickTrade(BotEntry entry, Character agent) {
        AgentTradeTickRuntimeService.tickTrade(
                entry,
                agent,
                AgentBotInventoryRuntimeAdapters.tradeTickRuntimeCallbacks(),
                AgentBotInventoryRuntimeAdapters.tradeRuntimeCallbacks(entry, agent),
                AgentBotInventoryRuntimeAdapters.tradeLifecycleRuntimeCallbacks());
    }
}
