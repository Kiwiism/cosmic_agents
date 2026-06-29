package server.agents.capabilities.shop;

import client.inventory.Item;
import client.inventory.WeaponType;
import constants.inventory.ItemConstants;

import java.util.function.IntUnaryOperator;

public final class AgentShopAmmoPolicy {
    private AgentShopAmmoPolicy() {
    }

    public static boolean needsFixedAmmoWeapon(WeaponType weaponType) {
        return weaponType == WeaponType.BOW || weaponType == WeaponType.CROSSBOW;
    }

    public static int triggerThreshold(int lowWarnCount, int triggerMultiplier) {
        return lowWarnCount * triggerMultiplier;
    }

    public static int targetThreshold(int lowWarnCount, int targetMultiplier) {
        return lowWarnCount * targetMultiplier;
    }

    public static boolean shouldBuyFixedAmmo(WeaponType weaponType, int currentCount, int targetThreshold) {
        return needsFixedAmmoWeapon(weaponType) && currentCount < targetThreshold;
    }

    public static boolean isRechargeWeaponType(WeaponType weaponType) {
        return weaponType == WeaponType.CLAW || weaponType == WeaponType.GUN;
    }

    public static boolean matchesRechargeWeapon(int itemId, WeaponType weaponType) {
        return switch (weaponType) {
            case CLAW -> ItemConstants.isThrowingStar(itemId);
            case GUN -> ItemConstants.isBullet(itemId);
            default -> false;
        };
    }

    public static int bestRechargeAmmoId(Iterable<Item> items,
                                         WeaponType weaponType,
                                         IntUnaryOperator projectileWatk) {
        int bestId = -1;
        int bestAtk = -1;
        for (Item item : items) {
            int itemId = item.getItemId();
            if (!ItemConstants.isRechargeable(itemId) || !matchesRechargeWeapon(itemId, weaponType)) {
                continue;
            }
            int attack = projectileWatk.applyAsInt(itemId);
            if (attack > bestAtk) {
                bestAtk = attack;
                bestId = itemId;
            }
        }
        return bestId;
    }

    public static boolean needsRecharge(Iterable<Item> items,
                                        WeaponType weaponType,
                                        int threshold,
                                        IntUnaryOperator projectileWatk,
                                        IntUnaryOperator slotMaxLookup) {
        if (!isRechargeWeaponType(weaponType)) {
            return false;
        }
        int bestId = bestRechargeAmmoId(items, weaponType, projectileWatk);
        if (bestId < 0) {
            return false;
        }
        int slotMax = slotMaxLookup.applyAsInt(bestId);
        int count = 0;
        boolean refillable = false;
        for (Item item : items) {
            if (item.getItemId() != bestId) {
                continue;
            }
            count += item.getQuantity();
            if (item.getQuantity() < slotMax) {
                refillable = true;
            }
        }
        return refillable && count < threshold;
    }
}
