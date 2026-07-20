package server.agents.integration;

import client.inventory.Item;
import server.agents.capabilities.inventory.AgentInventoryReservationRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

/** Cross-capability adapter for consumers that must honor the inventory reservation ledger. */
public final class AgentInventoryReservationGatewayRuntime {
    private AgentInventoryReservationGatewayRuntime() {
    }

    public static boolean mayConsume(AgentRuntimeEntry entry, Item item, long nowMs) {
        return AgentInventoryReservationRuntime.mayConsume(entry, item, nowMs);
    }

    public static List<Item> unreservedItems(AgentRuntimeEntry entry, List<Item> items, long nowMs) {
        return AgentInventoryReservationRuntime.unreservedItems(entry, items, nowMs);
    }
}
