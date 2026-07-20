package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.ItemRestrictionPolicy;
import server.agents.capabilities.equipment.AgentEquipmentReservePolicy;
import server.agents.capabilities.trade.AgentOfferService;
import server.agents.integration.InventoryGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class AgentInventorySellTrashService {
    private AgentInventorySellTrashService() {
    }

    public static List<Item> collectSellTrashEquips(AgentRuntimeEntry entry, Character agent, InventoryGateway inventory) {
        List<Item> normalTradeEquips = collectNormalTradeEquips(entry, agent, inventory);
        return collectSellTrashEquips(normalTradeEquips, inventory);
    }

    static List<Item> collectSellTrashEquips(List<Item> normalTradeEquips, InventoryGateway inventory) {
        if (normalTradeEquips.isEmpty()) {
            return normalTradeEquips;
        }

        List<Item> result = new ArrayList<>(normalTradeEquips.size());
        for (Item item : normalTradeEquips) {
            if (item instanceof Equip equip && !AgentInventorySellTrashPolicy.shouldKeepForSellTrash(inventory, equip)) {
                result.add(item);
            }
        }
        return result;
    }

    private static List<Item> collectNormalTradeEquips(AgentRuntimeEntry entry, Character agent, InventoryGateway inventory) {
        List<Item> all = AgentInventoryItemPolicy.collectSafeItems(agent, InventoryType.EQUIP, item -> true,
                inventory::isQuestItem,
                itemId -> ItemRestrictionPolicy.allowsUntradeable(agent, itemId));
        Set<Item> selfKeep = AgentEquipmentReservePolicy.collectPotentialSelfUpgradeItems(agent);
        long nowMs = System.currentTimeMillis();
        AgentInventoryReservationRuntime.refreshEquipmentReservations(entry, agent, nowMs);

        List<Item> normal = new ArrayList<>();
        for (Item item : all) {
            if (selfKeep.contains(item)) {
                continue;
            }
            if (!AgentInventoryReservationRuntime.mayConsume(entry, item, nowMs)) {
                continue;
            }
            if (AgentOfferService.isReservedForOtherRecipients(entry, agent, item)) {
                continue;
            }
            normal.add(item);
        }
        return AgentInventoryTradePolicy.sortEquipsByItemId(normal);
    }
}
