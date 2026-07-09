package server.agents.capabilities.equipment;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import server.agents.integration.InventoryGateway;
import server.agents.integration.cosmic.CosmicAgentServerAdapter;

import java.util.ArrayList;
import java.util.List;

public final class AgentEquipmentUnequipService {
    private AgentEquipmentUnequipService() {
    }

    interface UnequipHooks {
        boolean isCash(int itemId);
        String itemName(int itemId);
        void move(Character agent, short sourceSlot, short destinationSlot);

        static UnequipHooks live(InventoryGateway inventory) {
            return new UnequipHooks() {
                @Override public boolean isCash(int itemId) {
                    return inventory.isCashItem(itemId);
                }
                @Override public String itemName(int itemId) {
                    return inventory.getItemName(itemId);
                }
                @Override public void move(Character agent, short sourceSlot, short destinationSlot) {
                    InventoryManipulator.handleItemMove(
                            agent.getClient(), InventoryType.EQUIP, sourceSlot, destinationSlot, (short) 1);
                }
            };
        }
    }

    public static String unequipAll(Character agent) {
        return unequipAll(agent, UnequipHooks.live(CosmicAgentServerAdapter.INSTANCE.inventory()));
    }

    static String unequipAll(Character agent, UnequipHooks hooks) {
        Inventory equipInventory = agent.getInventory(InventoryType.EQUIP);
        Inventory equippedInventory = agent.getInventory(InventoryType.EQUIPPED);

        List<Short> equippedSlots = new ArrayList<>();
        for (Item item : equippedInventory.list()) {
            if (hooks.isCash(item.getItemId())) continue;
            equippedSlots.add(item.getPosition());
        }
        if (equippedSlots.isEmpty()) return "nothing to unequip";

        int freeSlots = equipInventory.getNumFreeSlot();
        if (freeSlots < equippedSlots.size()) {
            return "need " + equippedSlots.size() + " free equip slots, only have " + freeSlots;
        }

        equippedSlots.sort(Short::compare);
        for (short sourceSlot : equippedSlots) {
            short destinationSlot = equipInventory.getNextFreeSlot();
            if (destinationSlot < 0) {
                return "ran out of equip slots while unequipping";
            }
            hooks.move(agent, sourceSlot, destinationSlot);
        }
        return "unequipped " + equippedSlots.size() + " item" + (equippedSlots.size() != 1 ? "s" : "");
    }

    public static String unequipSlot(Character agent, short[] slots) {
        return unequipSlot(agent, slots, UnequipHooks.live(CosmicAgentServerAdapter.INSTANCE.inventory()));
    }

    static String unequipSlot(Character agent, short[] slots, UnequipHooks hooks) {
        Inventory equipInventory = agent.getInventory(InventoryType.EQUIP);
        Inventory equippedInventory = agent.getInventory(InventoryType.EQUIPPED);

        List<Short> toUnequip = new ArrayList<>();
        for (short slot : slots) {
            Item item = equippedInventory.getItem(slot);
            if (item != null && !hooks.isCash(item.getItemId())) {
                toUnequip.add(slot);
            }
        }
        if (toUnequip.isEmpty()) {
            return "nothing equipped there";
        }
        if (equipInventory.getNumFreeSlot() < toUnequip.size()) {
            return "equip bag full";
        }
        StringBuilder names = new StringBuilder();
        for (short sourceSlot : toUnequip) {
            Item item = equippedInventory.getItem(sourceSlot);
            short destinationSlot = equipInventory.getNextFreeSlot();
            if (destinationSlot < 0) return "ran out of equip slots";
            hooks.move(agent, sourceSlot, destinationSlot);
            if (!names.isEmpty()) names.append(", ");
            names.append(hooks.itemName(item.getItemId()));
        }
        return "unequipped " + names;
    }
}
