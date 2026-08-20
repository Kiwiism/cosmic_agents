package server.agents.capabilities.combat;

import client.inventory.WeaponType;
import constants.skills.Assassin;
import constants.skills.Bandit;
import constants.skills.ChiefBandit;
import constants.skills.DragonKnight;
import constants.skills.Hermit;
import constants.skills.NightLord;
import constants.skills.Pirate;
import constants.skills.Rogue;
import constants.skills.Shadower;
import constants.skills.Spearman;

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
        return canUseSkillWithWeapon(skillId, weaponType);
    }

    public static boolean canUseSkillWithWeapon(int skillId, WeaponType weaponType) {
        return switch (skillId) {
            case Pirate.FLASH_FIST -> weaponType == WeaponType.KNUCKLE;
            case Pirate.DOUBLE_SHOT -> weaponType == WeaponType.GUN;
            case Rogue.LUCKY_SEVEN, Assassin.DRAIN, Assassin.CLAW_BOOSTER,
                    Hermit.AVENGER, NightLord.TRIPLE_THROW ->
                    weaponType == WeaponType.CLAW;
            case Rogue.DOUBLE_STAB, Bandit.DAGGER_BOOSTER, Bandit.STEAL, Bandit.SAVAGE_BLOW,
                    ChiefBandit.ASSAULTER, ChiefBandit.BAND_OF_THIEVES,
                    Shadower.ASSASSINATE, Shadower.TAUNT, Shadower.BOOMERANG_STEP ->
                    isThiefDagger(weaponType);
            case Spearman.SPEAR_BOOSTER,
                    DragonKnight.SPEAR_CRUSHER, DragonKnight.SPEAR_DRAGON_FURY ->
                    isSpearWeapon(weaponType);
            case Spearman.POLEARM_BOOSTER,
                    DragonKnight.POLE_ARM_CRUSHER, DragonKnight.POLE_ARM_DRAGON_FURY ->
                    isPolearmWeapon(weaponType);
            default -> true;
        };
    }

    public static boolean isSpearWeapon(WeaponType weaponType) {
        return weaponType == WeaponType.SPEAR_STAB || weaponType == WeaponType.SPEAR_SWING;
    }

    public static boolean isPolearmWeapon(WeaponType weaponType) {
        return weaponType == WeaponType.POLE_ARM_SWING || weaponType == WeaponType.POLE_ARM_STAB;
    }

    public static boolean isThiefDagger(WeaponType weaponType) {
        return weaponType == WeaponType.DAGGER_OTHER || weaponType == WeaponType.DAGGER_THIEVES;
    }
}
