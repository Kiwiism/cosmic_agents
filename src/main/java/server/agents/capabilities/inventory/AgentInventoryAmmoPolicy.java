package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import constants.inventory.ItemConstants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

public final class AgentInventoryAmmoPolicy {
    public record AmmoTradeGroups(List<Item> nonOwn, List<Item> own) {
        public List<Item> itemsFor(AgentInventoryTradePolicy.AmmoGroup group) {
            return switch (group) {
                case NON_OWN -> nonOwn;
                case OWN -> own;
            };
        }
    }

    private AgentInventoryAmmoPolicy() {
    }

    public static boolean isAmmoForWeapon(int itemId, WeaponType weaponType) {
        return switch (weaponType) {
            case BOW -> ItemConstants.isArrowForBow(itemId);
            case CROSSBOW -> ItemConstants.isArrowForCrossBow(itemId);
            case CLAW -> ItemConstants.isThrowingStar(itemId);
            case GUN -> ItemConstants.isBullet(itemId);
            default -> false;
        };
    }

    public static boolean isTradeAmmoItem(int itemId) {
        return ammoWeaponType(itemId) != null;
    }

    public static WeaponType ammoWeaponType(int itemId) {
        if (ItemConstants.isArrowForBow(itemId)) {
            return WeaponType.BOW;
        }
        if (ItemConstants.isArrowForCrossBow(itemId)) {
            return WeaponType.CROSSBOW;
        }
        if (ItemConstants.isThrowingStar(itemId)) {
            return WeaponType.CLAW;
        }
        if (ItemConstants.isBullet(itemId)) {
            return WeaponType.GUN;
        }
        return null;
    }

    public static WeaponType tradeAmmoWeaponType(WeaponType weaponType) {
        return switch (weaponType) {
            case BOW, CROSSBOW, CLAW, GUN -> weaponType;
            default -> null;
        };
    }

    public static List<Item> collectShareItems(Character donorAgent,
                                               WeaponType needyWeaponType,
                                               int maxQty,
                                               IntUnaryOperator projectileWatkLookup) {
        if (maxQty <= 0) return List.of();
        List<Item> candidates = new ArrayList<>();
        Inventory useInv = donorAgent.getInventory(InventoryType.USE);
        for (short slot = 1; slot <= useInv.getSlotLimit(); slot++) {
            Item item = useInv.getItem(slot);
            if (item == null || !isAmmoForWeapon(item.getItemId(), needyWeaponType)) {
                continue;
            }
            candidates.add(item);
        }
        candidates.sort(Comparator
                .comparingInt((Item item) -> projectileWatkLookup.applyAsInt(item.getItemId()))
                .thenComparingInt(Item::getItemId));

        List<Item> result = new ArrayList<>();
        int totalQty = 0;
        for (Item item : candidates) {
            result.add(item);
            totalQty += item.getQuantity();
            if (result.size() >= 9 || totalQty >= maxQty) {
                break;
            }
        }
        return result;
    }

    public static AmmoTradeGroups classifyTradeGroups(Character agent,
                                                      WeaponType equippedWeaponType,
                                                      IntUnaryOperator projectileWatkLookup,
                                                      IntPredicate isQuestItem,
                                                      boolean untradeableItemsTradeable) {
        List<Item> nonOwn = new ArrayList<>();
        List<Item> own = new ArrayList<>();
        WeaponType ownAmmoWeaponType = tradeAmmoWeaponType(equippedWeaponType);
        nonOwn.addAll(AgentInventoryItemPolicy.collectSafeItems(agent, InventoryType.USE, item -> {
            WeaponType ammoType = ammoWeaponType(item.getItemId());
            if (ammoType == null) {
                return false;
            }
            if (ammoType == ownAmmoWeaponType) {
                own.add(item);
                return false;
            }
            return true;
        }, isQuestItem, untradeableItemsTradeable));
        nonOwn.sort(Comparator.comparingInt(Item::getItemId));
        own.sort(Comparator
                .comparingInt((Item item) -> projectileWatkLookup.applyAsInt(item.getItemId()))
                .thenComparingInt(Item::getItemId));
        return new AmmoTradeGroups(nonOwn, own);
    }

    public static AgentInventoryTradePolicy.AmmoGroup firstAvailableGroup(AmmoTradeGroups groups) {
        for (AgentInventoryTradePolicy.AmmoGroup group : AgentInventoryTradePolicy.AmmoGroup.values()) {
            if (!groups.itemsFor(group).isEmpty()) {
                return group;
            }
        }
        return null;
    }

    public static String nextAvailableGroupCategory(String category, AmmoTradeGroups groups) {
        AgentInventoryTradePolicy.AmmoGroup current = AgentInventoryTradePolicy.ammoGroupFromCategory(category);
        if (current == null) return null;
        for (AgentInventoryTradePolicy.AmmoGroup group = current.next(); group != null; group = group.next()) {
            if (!groups.itemsFor(group).isEmpty()) {
                return group.categoryString();
            }
        }
        return null;
    }
}
