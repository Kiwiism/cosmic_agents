package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import server.ItemInformationProvider;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.Function;

public final class AgentEquippedSlotTradeService {
    static IntPredicate cashItemLookup = itemId -> ItemInformationProvider.getInstance().isCash(itemId);

    public record PreparedTradeItems(List<Item> items, String errorMessage) {
    }

    private AgentEquippedSlotTradeService() {
    }

    public static boolean hasEquippedSlotItems(Character agent,
                                               String fragment,
                                               Function<String, short[]> slotResolver) {
        return countEquippedSlotItems(agent, fragment, slotResolver) > 0;
    }

    public static int countEquippedSlotItems(Character agent,
                                             String fragment,
                                             Function<String, short[]> slotResolver) {
        short[] slots = slotResolver.apply(fragment);
        if (slots.length == 0) {
            return 0;
        }

        Inventory equipped = agent.getInventory(InventoryType.EQUIPPED);
        int total = 0;
        for (short slot : slots) {
            Item item = equipped.getItem(slot);
            if (item != null && !cashItemLookup.test(item.getItemId())) {
                total++;
            }
        }
        return total;
    }

    public static PreparedTradeItems prepareEquippedSlotTradeItems(String fragment,
                                                                   AgentRuntimeEntry entry,
                                                                   Character agent,
                                                                   Function<String, short[]> slotResolver,
                                                                   Runnable restoreTemporarilyUnequippedItems) {
        short[] slots = slotResolver.apply(fragment);
        if (slots.length == 0) {
            return new PreparedTradeItems(List.of(), null);
        }

        Inventory equipped = agent.getInventory(InventoryType.EQUIPPED);
        Inventory equipBag = agent.getInventory(InventoryType.EQUIP);
        List<Short> occupiedSlots = new ArrayList<>();
        for (short slot : slots) {
            Item item = equipped.getItem(slot);
            if (item != null && !cashItemLookup.test(item.getItemId())) {
                occupiedSlots.add(slot);
            }
        }
        if (occupiedSlots.isEmpty()) {
            return new PreparedTradeItems(List.of(), null);
        }
        if (equipBag.getNumFreeSlot() < occupiedSlots.size()) {
            return new PreparedTradeItems(List.of(), AgentDialogueCatalog.tradeEquipBagFullReply());
        }

        occupiedSlots.sort(Short::compare);
        List<Item> result = new ArrayList<>();
        for (short srcSlot : occupiedSlots) {
            short dstSlot = equipBag.getNextFreeSlot();
            if (dstSlot < 0) {
                restoreTemporarilyUnequippedItems.run();
                return new PreparedTradeItems(List.of(), AgentDialogueCatalog.tradeEquipSlotsFullReply());
            }

            InventoryManipulator.handleItemMove(agent.getClient(), InventoryType.EQUIP, srcSlot, dstSlot, (short) 1);
            Item moved = equipBag.getItem(dstSlot);
            if (moved == null) {
                restoreTemporarilyUnequippedItems.run();
                return new PreparedTradeItems(List.of(), AgentDialogueCatalog.tradeEquippedItemPrepareFailedReply());
            }

            AgentBotPendingTradeStateRuntime.rememberRestoreSlot(entry, moved, srcSlot);
            result.add(moved);
        }

        return new PreparedTradeItems(result, null);
    }

    public static void restoreTemporarilyUnequippedItems(AgentRuntimeEntry entry, Character agent) {
        if (agent == null || !AgentBotPendingTradeStateRuntime.hasRestoreSlots(entry)) {
            AgentBotPendingTradeStateRuntime.clearRestoreSlots(entry);
            return;
        }

        Inventory equipped = agent.getInventory(InventoryType.EQUIPPED);
        List<Map.Entry<Item, Short>> restoreEntries = AgentBotPendingTradeStateRuntime.restoreSlotEntries(entry);
        restoreEntries.sort(Comparator.comparingInt(Map.Entry::getValue));
        for (Map.Entry<Item, Short> restoreEntry : restoreEntries) {
            Item item = restoreEntry.getKey();
            short dstSlot = restoreEntry.getValue();
            if (!AgentInventoryItemPolicy.hasItem(agent, item) || equipped.getItem(dstSlot) != null) {
                continue;
            }
            InventoryManipulator.handleItemMove(agent.getClient(), InventoryType.EQUIP, item.getPosition(), dstSlot, (short) 1);
        }
        AgentBotPendingTradeStateRuntime.clearRestoreSlots(entry);
    }
}
