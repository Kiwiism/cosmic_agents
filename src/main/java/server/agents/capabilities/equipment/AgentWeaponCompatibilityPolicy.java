package server.agents.capabilities.equipment;

import client.Character;
import client.Job;
import client.inventory.WeaponType;
import constants.skills.Crusader;
import constants.skills.DragonKnight;
import constants.skills.Fighter;
import constants.skills.Page;
import constants.skills.Paladin;
import constants.skills.Pirate;
import constants.skills.Rogue;
import constants.skills.Spearman;
import constants.skills.WhiteKnight;

public final class AgentWeaponCompatibilityPolicy {
    private AgentWeaponCompatibilityPolicy() {
    }

    public static boolean isMageJob(Job job) {
        return job == Job.MAGICIAN
                || job == Job.FP_WIZARD || job == Job.FP_MAGE || job == Job.FP_ARCHMAGE
                || job == Job.IL_WIZARD || job == Job.IL_MAGE || job == Job.IL_ARCHMAGE
                || job == Job.CLERIC || job == Job.PRIEST || job == Job.BISHOP;
    }

    public static boolean isWeaponCompatible(Character agent, WeaponType weaponType) {
        if (weaponType == null || weaponType == WeaponType.NOT_A_WEAPON) {
            return true;
        }

        Job job = agent.getJob();
        if (job == Job.THIEF) {
            if (agent.getSkillLevel(Rogue.LUCKY_SEVEN) > 0) {
                return weaponType == WeaponType.CLAW;
            }
            if (agent.getSkillLevel(Rogue.DOUBLE_STAB) > 0) {
                return isThiefDagger(weaponType);
            }
        }
        if (job == Job.PIRATE) {
            boolean gunBuild = agent.getSkillLevel(Pirate.DOUBLE_SHOT) > 0;
            boolean knuckleBuild = agent.getSkillLevel(Pirate.FLASH_FIST) > 0
                    || agent.getSkillLevel(Pirate.SOMERSAULT_KICK) > 0;
            if (gunBuild && !knuckleBuild) {
                return weaponType == WeaponType.GUN;
            }
            if (knuckleBuild && !gunBuild) {
                return weaponType == WeaponType.KNUCKLE;
            }
            return weaponType == WeaponType.GUN || weaponType == WeaponType.KNUCKLE;
        }

        return switch (job) {
            case BOWMAN -> weaponType == WeaponType.BOW || weaponType == WeaponType.CROSSBOW;
            case FIGHTER -> matchesWarriorWeaponFamily(agent,
                    isSword(weaponType), isGeneralWeapon(weaponType),
                    new int[]{Fighter.SWORD_MASTERY, Fighter.SWORD_BOOSTER},
                    new int[]{Fighter.AXE_MASTERY, Fighter.AXE_BOOSTER});
            case CRUSADER -> matchesWarriorWeaponFamily(agent,
                    isSword(weaponType), isGeneralWeapon(weaponType),
                    new int[]{Crusader.SWORD_COMA, Crusader.SWORD_PANIC},
                    new int[]{Crusader.AXE_COMA, Crusader.AXE_PANIC});
            case HERO -> matchesWarriorWeaponFamily(agent,
                    isSword(weaponType), isGeneralWeapon(weaponType),
                    new int[]{Crusader.SWORD_COMA, Crusader.SWORD_PANIC},
                    new int[]{Crusader.AXE_COMA, Crusader.AXE_PANIC});
            case PAGE -> matchesWarriorWeaponFamily(agent,
                    isSword(weaponType), isGeneralWeapon(weaponType),
                    new int[]{Page.SWORD_MASTERY, Page.SWORD_BOOSTER},
                    new int[]{Page.BW_MASTERY, Page.BW_BOOSTER});
            case WHITEKNIGHT -> matchesWarriorWeaponFamily(agent,
                    isSword(weaponType), isGeneralWeapon(weaponType),
                    new int[]{WhiteKnight.SWORD_FIRE_CHARGE, WhiteKnight.SWORD_ICE_CHARGE, WhiteKnight.SWORD_LIT_CHARGE},
                    new int[]{WhiteKnight.BW_FIRE_CHARGE, WhiteKnight.BW_ICE_CHARGE, WhiteKnight.BW_LIT_CHARGE});
            case PALADIN -> matchesWarriorWeaponFamily(agent,
                    isSword(weaponType), isGeneralWeapon(weaponType),
                    new int[]{Paladin.SWORD_HOLY_CHARGE},
                    new int[]{Paladin.BW_HOLY_CHARGE});
            case SPEARMAN -> matchesWarriorWeaponFamily(agent,
                    isSpearWeapon(weaponType), isPolearmWeapon(weaponType),
                    new int[]{Spearman.SPEAR_MASTERY, Spearman.SPEAR_BOOSTER},
                    new int[]{Spearman.POLEARM_MASTERY, Spearman.POLEARM_BOOSTER});
            case DRAGONKNIGHT -> matchesWarriorWeaponFamily(agent,
                    isSpearWeapon(weaponType), isPolearmWeapon(weaponType),
                    new int[]{DragonKnight.SPEAR_CRUSHER, DragonKnight.SPEAR_DRAGON_FURY},
                    new int[]{DragonKnight.POLE_ARM_CRUSHER, DragonKnight.POLE_ARM_DRAGON_FURY});
            case DARKKNIGHT -> matchesWarriorWeaponFamily(agent,
                    isSpearWeapon(weaponType), isPolearmWeapon(weaponType),
                    new int[]{DragonKnight.SPEAR_CRUSHER, DragonKnight.SPEAR_DRAGON_FURY},
                    new int[]{DragonKnight.POLE_ARM_CRUSHER, DragonKnight.POLE_ARM_DRAGON_FURY});
            case MAGICIAN, FP_WIZARD, FP_MAGE, FP_ARCHMAGE, IL_WIZARD, IL_MAGE, IL_ARCHMAGE, CLERIC, PRIEST, BISHOP ->
                    weaponType == WeaponType.WAND || weaponType == WeaponType.STAFF;
            case HUNTER, RANGER, BOWMASTER -> weaponType == WeaponType.BOW;
            case CROSSBOWMAN, SNIPER, MARKSMAN -> weaponType == WeaponType.CROSSBOW;
            case ASSASSIN, HERMIT, NIGHTLORD -> weaponType == WeaponType.CLAW;
            case BANDIT, CHIEFBANDIT, SHADOWER -> isThiefDagger(weaponType);
            case BRAWLER, MARAUDER, BUCCANEER -> weaponType == WeaponType.KNUCKLE;
            case GUNSLINGER, OUTLAW, CORSAIR -> weaponType == WeaponType.GUN;
            default -> true;
        };
    }

    public static String weaponUsefulnessTrackKey(Character agent, WeaponType weaponType) {
        if (!isWeaponCompatible(agent, weaponType)) {
            return null;
        }
        if (weaponType == null || weaponType == WeaponType.NOT_A_WEAPON) {
            return "non-weapon";
        }
        if (isSword(weaponType)) {
            return "sword";
        }
        if (isGeneralWeapon(weaponType)) {
            return "general";
        }
        if (isSpearWeapon(weaponType)) {
            return "spear";
        }
        if (isPolearmWeapon(weaponType)) {
            return "polearm";
        }
        if (isThiefDagger(weaponType)) {
            return "thief-dagger";
        }
        return switch (weaponType) {
            case BOW -> "bow";
            case CROSSBOW -> "crossbow";
            case CLAW -> "claw";
            case GUN -> "gun";
            case KNUCKLE -> "knuckle";
            case WAND -> "wand";
            case STAFF -> "staff";
            default -> weaponType.name();
        };
    }

    private static boolean matchesWarriorWeaponFamily(Character agent,
                                                      boolean firstFamilyMatch,
                                                      boolean secondFamilyMatch,
                                                      int[] firstFamilySkills,
                                                      int[] secondFamilySkills) {
        boolean firstFamilyChosen = hasAnySkill(agent, firstFamilySkills);
        boolean secondFamilyChosen = hasAnySkill(agent, secondFamilySkills);
        if (firstFamilyChosen && !secondFamilyChosen) {
            return firstFamilyMatch;
        }
        if (secondFamilyChosen && !firstFamilyChosen) {
            return secondFamilyMatch;
        }
        return firstFamilyMatch || secondFamilyMatch;
    }

    private static boolean hasAnySkill(Character agent, int... skillIds) {
        for (int skillId : skillIds) {
            if (agent.getSkillLevel(skillId) > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSword(WeaponType weaponType) {
        return weaponType == WeaponType.SWORD1H || weaponType == WeaponType.SWORD2H;
    }

    private static boolean isGeneralWeapon(WeaponType weaponType) {
        return weaponType == WeaponType.GENERAL1H_SWING
                || weaponType == WeaponType.GENERAL1H_STAB
                || weaponType == WeaponType.GENERAL2H_SWING
                || weaponType == WeaponType.GENERAL2H_STAB;
    }

    private static boolean isSpearWeapon(WeaponType weaponType) {
        return weaponType == WeaponType.SPEAR_STAB || weaponType == WeaponType.SPEAR_SWING;
    }

    private static boolean isPolearmWeapon(WeaponType weaponType) {
        return weaponType == WeaponType.POLE_ARM_SWING || weaponType == WeaponType.POLE_ARM_STAB;
    }

    private static boolean isThiefDagger(WeaponType weaponType) {
        return weaponType == WeaponType.DAGGER_OTHER || weaponType == WeaponType.DAGGER_THIEVES;
    }
}
