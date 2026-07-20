package server.agents.capabilities.equipment;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.InventoryGateway;

import java.util.Collection;
import java.util.List;

/**
 * Agent-owned equipment boundary over the remaining legacy optimizer.
 * Subsequent slices should move implementations here and delete the bot shell.
 */
public final class AgentEquipmentService {
    private AgentEquipmentService() {
    }

    public static void autoEquip(Character agent, Character leader, Item pendingOffer) {
        AgentEquipmentAutoEquipService.autoEquip(agent, leader, pendingOffer);
    }

    public static void autoEquip(Character agent, Character leader, Item pendingOffer, boolean force) {
        AgentEquipmentAutoEquipService.autoEquip(agent, leader, pendingOffer, force);
    }

    /** Selects a catalog-authored starter weapon after the general optimizer reconciles the loadout. */
    public static boolean equipPreferredWeapon(Character agent, int itemId) {
        return equipPreferredWeapon(agent, itemId, AgentInventoryGatewayRuntime.inventory());
    }

    static boolean equipPreferredWeapon(Character agent, int itemId, InventoryGateway inventory) {
        if (agent == null || itemId <= 0 || inventory == null) {
            return false;
        }
        Item equipped = agent.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        if (equipped != null && equipped.getItemId() == itemId) {
            return true;
        }
        Inventory equipInventory = agent.getInventory(InventoryType.EQUIP);
        for (Item item : equipInventory.list()) {
            if (item instanceof Equip equip && equip.getItemId() == itemId && equip.getPosition() > 0
                    && inventory.canWearEquipment(agent, equip, (short) -11)) {
                inventory.moveItem(agent, InventoryType.EQUIP, equip.getPosition(), (short) -11, (short) 1);
                Item selected = agent.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
                return selected != null && selected.getItemId() == itemId;
            }
        }
        return false;
    }

    public static List<String> autoEquipDebug(Character agent) {
        return AgentEquipmentAutoEquipService.autoEquipDebug(agent);
    }

    public static String unequipSlot(Character agent, short[] slots) {
        return AgentEquipmentUnequipService.unequipSlot(agent, slots);
    }

    public static String unequipAll(Character agent) {
        return AgentEquipmentUnequipService.unequipAll(agent);
    }

    public static short[] slotsFromName(String slotName) {
        return AgentEquipmentSlotResolver.slotsFromName(slotName);
    }

    public static List<AgentEquipRecommendation> findRecommendedEquips(Character agent, Character leader) {
        return AgentEquipmentRecommendationService.findRecommendedEquips(agent, leader);
    }

    public static List<AgentEquipRecommendation> findRecommendedEquipsFromItems(Character agent,
                                                                                Collection<Equip> items) {
        return AgentEquipmentRecommendationService.findRecommendedEquipsFromItems(agent, items);
    }

    public static AgentEquipRecommendation findRecommendationForItem(Character agent,
                                                                     Character leader,
                                                                     Item item) {
        return AgentEquipmentRecommendationService.findRecommendationForItem(agent, leader, item);
    }

    public static boolean shouldReserveOwnedItem(Character agent, Item item) {
        return AgentEquipmentReservePolicy.shouldReserveOwnedItem(agent, item);
    }

    public static boolean wouldReserveIncomingItem(Character agent, Equip equip) {
        return AgentEquipmentReservePolicy.wouldReserveIncomingItem(agent, equip);
    }

    public static boolean isWeaponCompatible(Character agent, WeaponType weaponType) {
        return AgentWeaponCompatibilityPolicy.isWeaponCompatible(agent, weaponType);
    }

    public static boolean isMageJob(Job job) {
        return AgentWeaponCompatibilityPolicy.isMageJob(job);
    }
}
