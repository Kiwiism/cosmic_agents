package server.agents.capabilities.equipment;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.Item;
import client.inventory.WeaponType;
import server.ItemInformationProvider;
import server.bots.BotEquipManager;

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
        BotEquipManager.autoEquip(agent, leader, pendingOffer);
    }

    public static void autoEquip(Character agent, Character leader, Item pendingOffer, boolean force) {
        BotEquipManager.autoEquip(agent, leader, pendingOffer, force);
    }

    public static List<String> autoEquipDebug(Character agent) {
        return BotEquipManager.autoEquipDebug(agent);
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

    public static boolean wouldReserveIncomingItem(Character agent, ItemInformationProvider ii, Equip equip) {
        return AgentEquipmentReservePolicy.wouldReserveIncomingItem(agent, ii, equip);
    }

    public static boolean isWeaponCompatible(Character agent, WeaponType weaponType) {
        return AgentWeaponCompatibilityPolicy.isWeaponCompatible(agent, weaponType);
    }

    public static boolean isMageJob(Job job) {
        return AgentWeaponCompatibilityPolicy.isMageJob(job);
    }
}
