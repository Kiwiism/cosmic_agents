package server.agents.capabilities.equipment;

import client.Character;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import server.ItemInformationProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies optimizer-selected equipment plans to the live Agent character.
 */
public final class AgentEquipmentPlanExecutor {
    private AgentEquipmentPlanExecutor() {
    }

    public static void applyEquipPlan(Character agent,
                                      Map<Short, Equip> currentBySlot,
                                      Map<Short, Equip> picks,
                                      Equip targetWeapon,
                                      List<Short> dpSlots) {
        List<Short> order = new ArrayList<>();
        order.add((short) -11);
        if (dpSlots.contains((short) -5)) {
            order.add((short) -5);
        }
        if (dpSlots.contains((short) -6)) {
            order.add((short) -6);
        }
        for (Short slot : dpSlots) {
            if (slot != (short) -5 && slot != (short) -6) {
                order.add(slot);
            }
        }
        Map<Short, Equip> full = new HashMap<>(picks);
        full.put((short) -11, targetWeapon);
        for (Short slot : order) {
            Equip target = full.get(slot);
            Equip current = currentBySlot.get(slot);
            if (target == null || target == current) {
                continue;
            }
            short position = target.getPosition();
            if (position <= 0) {
                continue;
            }
            InventoryManipulator.handleItemMove(agent.getClient(), InventoryType.EQUIP,
                    position, slot, (short) 1);
        }
    }

    public static void unequipInfeasibleEquipped(Character agent, ItemInformationProvider itemInfo) {
        Inventory equippedInventory = agent.getInventory(InventoryType.EQUIPPED);
        List<Short> bad = new ArrayList<>();
        for (Item item : equippedInventory.list()) {
            if (!(item instanceof Equip equip)) {
                continue;
            }
            if (itemInfo.isCash(equip.getItemId())) {
                continue;
            }
            if (!itemInfo.canWearEquipment(agent, equip, equip.getPosition())) {
                bad.add(equip.getPosition());
            }
        }
        if (bad.isEmpty()) {
            return;
        }
        short[] slots = new short[bad.size()];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = bad.get(i);
        }
        AgentEquipmentUnequipService.unequipSlot(agent, slots);
    }
}
