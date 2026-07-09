package server.agents.capabilities.equipment;

import client.Character;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.InventoryGateway;

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
        applyEquipPlan(agent, currentBySlot, picks, targetWeapon, dpSlots, AgentInventoryGatewayRuntime.inventory());
    }

    static void applyEquipPlan(Character agent,
                               Map<Short, Equip> currentBySlot,
                               Map<Short, Equip> picks,
                               Equip targetWeapon,
                               List<Short> dpSlots,
                               InventoryGateway inventory) {
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
            inventory.moveItem(agent, InventoryType.EQUIP, position, slot, (short) 1);
        }
    }

    public static void unequipInfeasibleEquipped(Character agent) {
        unequipInfeasibleEquipped(agent, InfeasibleEquipHooks.live(AgentInventoryGatewayRuntime.inventory()));
    }

    static void unequipInfeasibleEquipped(Character agent, InfeasibleEquipHooks hooks) {
        Inventory equippedInventory = agent.getInventory(InventoryType.EQUIPPED);
        List<Short> bad = new ArrayList<>();
        for (Item item : equippedInventory.list()) {
            if (!(item instanceof Equip equip)) {
                continue;
            }
            if (hooks.isCashItem(equip.getItemId())) {
                continue;
            }
            if (!hooks.canWearEquipment(agent, equip, equip.getPosition())) {
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

    interface InfeasibleEquipHooks {
        boolean isCashItem(int itemId);

        boolean canWearEquipment(Character agent, Equip equip, short primarySlot);

        static InfeasibleEquipHooks live(InventoryGateway inventory) {
            return new InfeasibleEquipHooks() {
                @Override
                public boolean isCashItem(int itemId) {
                    return inventory.isCashItem(itemId);
                }

                @Override
                public boolean canWearEquipment(Character agent, Equip equip, short primarySlot) {
                    return inventory.canWearEquipment(agent, equip, primarySlot);
                }
            };
        }
    }
}
