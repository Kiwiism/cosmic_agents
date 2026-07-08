package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.Item;
import server.agents.capabilities.looting.AgentPassiveLootRuntimeService;
import server.agents.capabilities.trade.AgentInventoryTransferService;
import server.agents.capabilities.trade.AgentManualTradeRuntimeService;
import server.agents.capabilities.trade.AgentTradeLifecycleRuntimeService;
import server.agents.capabilities.trade.AgentTradeTickRuntimeService;
import server.agents.capabilities.trade.AgentTradeTransferAvailabilityRuntimeService;
import server.agents.integration.AgentInventoryRuntimeAdapters;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned entry points for inventory, loot, and trade tick behavior.
 */
public final class AgentInventoryTickRuntime {
    private AgentInventoryTickRuntime() {
    }

    public static void tickPassiveLoot(AgentRuntimeEntry entry, Character agent) {
        AgentPassiveLootRuntimeService.tickPassiveLoot(
                entry,
                agent,
                AgentInventoryRuntimeAdapters.passiveLootRuntimeCallbacks());
    }

    public static void tickManualTrade(AgentRuntimeEntry entry, Character agent) {
        AgentManualTradeRuntimeService.tickManualTrade(
                entry,
                agent,
                AgentRuntimeIdentityRuntime.owner(entry),
                AgentInventoryRuntimeAdapters.manualTradeRuntimeCallbacks(entry),
                AgentTradeLifecycleRuntimeService.lifecycleCallbacks(
                        AgentInventoryRuntimeAdapters.tradeLifecycleRuntimeCallbacks()));
    }

    public static void executeChoice(String category, boolean tradeToLeader, AgentRuntimeEntry entry, Character agent) {
        AgentInventoryTransferService.executeChoice(category, tradeToLeader, entry, agent);
    }

    public static void startTradeTransfer(String category, AgentRuntimeEntry entry, Character agent) {
        AgentInventoryTransferService.startTradeTransfer(category, entry, agent);
    }

    public static void startTradeTransfer(Item item, Character recipient, AgentRuntimeEntry entry, Character agent) {
        AgentInventoryTransferService.startTradeTransfer(item, recipient, entry, agent);
    }

    public static boolean hasTransferableItems(String category, AgentRuntimeEntry entry, Character agent) {
        return AgentTradeTransferAvailabilityRuntimeService.hasTransferableItems(
                category,
                entry,
                agent,
                AgentInventoryRuntimeAdapters.transferAvailabilityRuntimeCallbacks(),
                AgentInventoryRuntimeAdapters.tradeRuntimeCallbacks(entry, agent));
    }

    public static int countTransferableItems(String category, AgentRuntimeEntry entry, Character agent) {
        return AgentTradeTransferAvailabilityRuntimeService.countTransferableItems(
                category,
                entry,
                agent,
                AgentInventoryRuntimeAdapters.transferAvailabilityRuntimeCallbacks(),
                AgentInventoryRuntimeAdapters.tradeRuntimeCallbacks(entry, agent));
    }

    public static void tickTrade(AgentRuntimeEntry entry, Character agent) {
        AgentTradeTickRuntimeService.tickTrade(
                entry,
                agent,
                AgentInventoryRuntimeAdapters.tradeTickRuntimeCallbacks(),
                AgentInventoryRuntimeAdapters.tradeRuntimeCallbacks(entry, agent),
                AgentInventoryRuntimeAdapters.tradeLifecycleRuntimeCallbacks());
    }
}
