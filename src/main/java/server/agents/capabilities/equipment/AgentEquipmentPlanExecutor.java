package server.agents.capabilities.equipment;

import client.Character;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.InventoryGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies optimizer-selected equipment plans to the live Agent character.
 */
public final class AgentEquipmentPlanExecutor {
    private static final Logger log = LoggerFactory.getLogger(AgentEquipmentPlanExecutor.class);

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

    static void relocateEquippedStrays(Character agent, Inventory equipInventory, Inventory equippedInventory) {
        List<Equip> strays = new ArrayList<>();
        for (Item item : equippedInventory.list()) {
            if (item instanceof Equip equip && equip.getPosition() >= 0) {
                strays.add(equip);
            }
        }
        for (Equip stray : strays) {
            short destination = equipInventory.getNextFreeSlot();
            if (destination < 0) {
                break;
            }
            short source = stray.getPosition();
            equippedInventory.lockInventory();
            try {
                equippedInventory.removeSlot(source);
            } finally {
                equippedInventory.unlockInventory();
            }
            stray.setPosition(destination);
            equipInventory.lockInventory();
            try {
                equipInventory.addItemFromDB(stray);
            } finally {
                equipInventory.unlockInventory();
            }
            log.warn("Agent '{}' had stray equip id {} in EQUIPPED slot {}; moved to EQUIP slot {}",
                    agent.getName(), stray.getItemId(), source, destination);
        }
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
            if (equip.getPosition() >= 0) {
                continue;
            }
            if (!hooks.meetsEquipRequirements(agent, equip)) {
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

        boolean meetsEquipRequirements(Character agent, Equip equip);

        static InfeasibleEquipHooks live(InventoryGateway inventory) {
            return new InfeasibleEquipHooks() {
                @Override
                public boolean isCashItem(int itemId) {
                    return inventory.isCashItem(itemId);
                }

                @Override
                public boolean meetsEquipRequirements(Character agent, Equip equip) {
                    return inventory.meetsEquipRequirements(equip, agent.getJob(), agent.getLevel(),
                            agent.getTotalStr(), agent.getTotalDex(), agent.getTotalInt(),
                            agent.getTotalLuk(), agent.getFame());
                }
            };
        }
    }
}
