package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.Item;
import server.agents.capabilities.contracts.AgentDisposition;
import server.agents.capabilities.contracts.AgentInventoryReservation;
import server.agents.capabilities.equipment.AgentEquipmentReservePolicy;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared policy surface used by every capability that may consume or protect inventory. */
public final class AgentInventoryReservationRuntime {
    public static final String EQUIPMENT_CAPABILITY = "equipment";
    public static final String LOOT_CAPABILITY = "loot";
    public static final String COMBAT_LOOT_CAPABILITY = "combat-loot";
    private static final long OBJECTIVE_RESERVATION_TTL_MS = 10 * 60_000L;

    private AgentInventoryReservationRuntime() {
    }

    public static AgentInventoryReservationLedger ledger(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentInventoryReservationLedger.STATE_KEY);
    }

    public static boolean isReserved(AgentRuntimeEntry entry, int itemId, long nowMs) {
        return entry != null && itemId > 0 && ledger(entry).reservedQuantity(itemId, nowMs) > 0;
    }

    public static boolean mayConsume(AgentRuntimeEntry entry, Item item, long nowMs) {
        return item != null && !isReserved(entry, item.getItemId(), nowMs);
    }

    public static List<Item> unreservedItems(AgentRuntimeEntry entry, List<Item> items, long nowMs) {
        if (entry == null || items == null || items.isEmpty()) {
            return items == null ? List.of() : List.copyOf(items);
        }
        return items.stream().filter(item -> mayConsume(entry, item, nowMs)).toList();
    }

    public static void reserveObjectiveItems(AgentRuntimeEntry entry,
                                             Map<Integer, Integer> requiredItemCounts,
                                             String capability,
                                             AgentDisposition disposition,
                                             String reason,
                                             int priority,
                                             long nowMs) {
        if (entry == null || requiredItemCounts == null || requiredItemCounts.isEmpty()) {
            return;
        }
        List<AgentInventoryReservation> reservations = requiredItemCounts.entrySet().stream()
                .filter(requirement -> requirement.getKey() > 0 && requirement.getValue() > 0)
                .map(requirement -> new AgentInventoryReservation(
                        capability + ':' + requirement.getKey(), requirement.getKey(), requirement.getValue(),
                        disposition, capability, reason, priority,
                        nowMs + OBJECTIVE_RESERVATION_TTL_MS))
                .toList();
        ledger(entry).replaceCapabilityReservations(capability, reservations);
    }

    public static void releaseCapability(AgentRuntimeEntry entry, String capability) {
        if (entry != null) {
            ledger(entry).releaseCapability(capability);
        }
    }

    /** Publishes equipment self-keep decisions into the cross-capability reservation ledger. */
    public static void refreshEquipmentReservations(AgentRuntimeEntry entry,
                                                    Character agent,
                                                    long nowMs) {
        if (entry == null || agent == null) {
            return;
        }
        Map<Integer, Integer> quantities = new LinkedHashMap<>();
        for (Item item : AgentEquipmentReservePolicy.collectPotentialSelfUpgradeItems(agent)) {
            quantities.merge(item.getItemId(), 1, Integer::sum);
        }
        List<AgentInventoryReservation> reservations = new ArrayList<>(quantities.size());
        quantities.forEach((itemId, quantity) -> reservations.add(new AgentInventoryReservation(
                EQUIPMENT_CAPABILITY + ':' + itemId, itemId, quantity,
                AgentDisposition.BUILD_RESERVE, EQUIPMENT_CAPABILITY,
                "candidate for the Agent's current or future build", 500, 0L)));
        ledger(entry).replaceCapabilityReservations(EQUIPMENT_CAPABILITY, reservations);
    }
}
