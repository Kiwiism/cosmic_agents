package server.agents.capabilities.inventory;

import client.inventory.WeaponType;
import constants.inventory.ItemConstants;

public final class AgentInventoryAmmoPolicy {
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
}
