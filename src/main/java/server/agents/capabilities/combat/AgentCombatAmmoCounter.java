package server.agents.capabilities.combat;

import client.BuffStat;
import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import constants.inventory.ItemConstants;

public final class AgentCombatAmmoCounter {
    private AgentCombatAmmoCounter() {
    }

    /** Returns true if the Agent's weapon type requires projectile ammo. */
    public static boolean isRangedAmmoWeapon(WeaponType weaponType) {
        return weaponType == WeaponType.BOW || weaponType == WeaponType.CROSSBOW
                || weaponType == WeaponType.CLAW || weaponType == WeaponType.GUN;
    }

    public static int countAmmo(Character agent, WeaponType weaponType) {
        if (weaponType == null || !isRangedAmmoWeapon(weaponType)) {
            return Integer.MAX_VALUE;
        }
        boolean soulArrow = agent.getBuffedValue(BuffStat.SOULARROW) != null;
        boolean shadowClaw = agent.getBuffedValue(BuffStat.SHADOW_CLAW) != null;
        if (soulArrow || shadowClaw) {
            return Integer.MAX_VALUE;
        }
        int total = 0;
        for (Item item : agent.getInventory(InventoryType.USE).list()) {
            int id = item.getItemId();
            boolean match = switch (weaponType) {
                case BOW -> ItemConstants.isArrowForBow(id);
                case CROSSBOW -> ItemConstants.isArrowForCrossBow(id);
                case CLAW -> ItemConstants.isThrowingStar(id);
                case GUN -> ItemConstants.isBullet(id);
                default -> false;
            };
            if (match) {
                total += item.getQuantity();
            }
        }
        return total;
    }
}
