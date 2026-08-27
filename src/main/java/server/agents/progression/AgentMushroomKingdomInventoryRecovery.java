package server.agents.progression;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.inventory.ItemConstants;
import server.ItemRestrictionPolicy;
import server.agents.capabilities.inventory.AgentInventoryItemPolicy;
import server.agents.capabilities.inventory.AgentInventoryReservationRuntime;
import server.agents.capabilities.inventory.AgentInventorySellTrashService;
import server.agents.capabilities.inventory.AgentUseItemClassificationPolicy;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.InventoryGateway;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Comparator;
import java.util.Set;

/** Frees one inventory slot without consuming quest, build, supply, or reserved items. */
final class AgentMushroomKingdomInventoryRecovery {
    private static final Set<Integer> PROTECTED_ITEMS = Set.of(
            4_032_375, 2_430_014, 2_430_015, 4_032_388, 4_032_405,
            4_032_387, 4_032_386, 4_001_318,
            4_000_499, 4_000_500, 4_000_501, 4_001_317, 4_000_502, 4_000_503,
            2_002_008);

    record Result(boolean recovered, int discardedItemId, String reason) {
        static Result none(String reason) { return new Result(false, 0, reason); }
    }

    private AgentMushroomKingdomInventoryRecovery() { }

    static Result freeSlot(AgentRuntimeEntry entry, Character agent, int requiredItemId,
                           PrimitiveCapabilityGateway gateway) {
        if (entry == null || agent == null || gateway == null) return Result.none("missing recovery context");
        if (gateway.freeSlots(agent, requiredItemId) > 0) return new Result(true, 0, "slot already free");
        InventoryType type = ItemConstants.getInventoryType(requiredItemId);
        InventoryGateway inventory = AgentInventoryGatewayRuntime.inventory();
        Item candidate = type == InventoryType.EQUIP
                ? trashEquip(entry, agent, inventory)
                : ordinaryJunk(entry, agent, type, inventory);
        if (candidate == null) return Result.none("no safe " + type + " junk can be discarded");
        inventory.dropItem(agent, type, candidate.getPosition(), candidate.getQuantity());
        boolean recovered = gateway.freeSlots(agent, requiredItemId) > 0;
        return new Result(recovered, candidate.getItemId(), recovered
                ? "discarded safe junk item " + candidate.getItemId()
                : "discarded item but no slot became available");
    }

    private static Item trashEquip(AgentRuntimeEntry entry, Character agent, InventoryGateway inventory) {
        return AgentInventorySellTrashService.collectSellTrashEquips(entry, agent, inventory).stream()
                .min(Comparator.comparingInt(Item::getItemId))
                .orElse(null);
    }

    private static Item ordinaryJunk(AgentRuntimeEntry entry, Character agent, InventoryType type,
                                     InventoryGateway inventory) {
        if (type == InventoryType.UNDEFINED || type == InventoryType.CASH
                || type == InventoryType.EQUIPPED) return null;
        Inventory bag = agent.getInventory(type);
        if (bag == null) return null;
        long nowMs = System.currentTimeMillis();
        for (short slot = 1; slot <= bag.getSlotLimit(); slot++) {
            Item item = bag.getItem(slot);
            if (item == null || PROTECTED_ITEMS.contains(item.getItemId())
                    || !AgentInventoryItemPolicy.isSafeToDrop(item, inventory::isQuestItem,
                    itemId -> ItemRestrictionPolicy.allowsUntradeable(agent, itemId))
                    || !AgentInventoryReservationRuntime.mayConsume(entry, item, nowMs)
                    || supplyItem(item.getItemId(), type)) continue;
            return item;
        }
        return null;
    }

    private static boolean supplyItem(int itemId, InventoryType type) {
        if (type != InventoryType.USE) return false;
        return AgentUseItemClassificationPolicy.isRecoveryPotion(itemId)
                || AgentUseItemClassificationPolicy.isBuffConsumable(itemId)
                || ItemConstants.isRechargeable(itemId)
                || ItemConstants.isArrow(itemId)
                || itemId / 10_000 == 203;
    }
}
