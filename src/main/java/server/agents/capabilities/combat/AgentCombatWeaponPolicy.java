package server.agents.capabilities.combat;

import client.inventory.WeaponType;
import constants.skills.DragonKnight;

public final class AgentCombatWeaponPolicy {
    private AgentCombatWeaponPolicy() {
    }

    public static WeaponType damageWeaponTypeForAction(int skillId, WeaponType equippedWeaponType, String action) {
        WeaponType skillForcedWeaponType = switch (skillId) {
            case DragonKnight.SPEAR_CRUSHER -> WeaponType.SPEAR_STAB;
            case DragonKnight.POLE_ARM_CRUSHER -> WeaponType.POLE_ARM_STAB;
            case DragonKnight.SPEAR_DRAGON_FURY -> WeaponType.SPEAR_SWING;
            case DragonKnight.POLE_ARM_DRAGON_FURY -> WeaponType.POLE_ARM_SWING;
            default -> null;
        };
        if (skillForcedWeaponType != null || action == null || equippedWeaponType == null) {
            return skillForcedWeaponType;
        }

        boolean stab = action.startsWith("stab");
        boolean swing = action.startsWith("swing");
        if (!stab && !swing) {
            return null;
        }

        return switch (equippedWeaponType) {
            case SPEAR_STAB, SPEAR_SWING -> stab ? WeaponType.SPEAR_STAB : WeaponType.SPEAR_SWING;
            case POLE_ARM_SWING, POLE_ARM_STAB -> stab ? WeaponType.POLE_ARM_STAB : WeaponType.POLE_ARM_SWING;
            case GENERAL1H_SWING, GENERAL1H_STAB -> stab ? WeaponType.GENERAL1H_STAB : WeaponType.GENERAL1H_SWING;
            case GENERAL2H_SWING, GENERAL2H_STAB -> stab ? WeaponType.GENERAL2H_STAB : WeaponType.GENERAL2H_SWING;
            default -> null;
        };
    }

    public static boolean canUseAttackSkillWithWeapon(int skillId, WeaponType weaponType) {
        return switch (skillId) {
            case DragonKnight.SPEAR_CRUSHER, DragonKnight.SPEAR_DRAGON_FURY -> isSpearWeapon(weaponType);
            case DragonKnight.POLE_ARM_CRUSHER, DragonKnight.POLE_ARM_DRAGON_FURY -> isPolearmWeapon(weaponType);
            default -> true;
        };
    }

    public static boolean isSpearWeapon(WeaponType weaponType) {
        return weaponType == WeaponType.SPEAR_STAB || weaponType == WeaponType.SPEAR_SWING;
    }

    public static boolean isPolearmWeapon(WeaponType weaponType) {
        return weaponType == WeaponType.POLE_ARM_SWING || weaponType == WeaponType.POLE_ARM_STAB;
    }
}
