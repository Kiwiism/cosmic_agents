package server.agents.capabilities.combat;

import client.Character;
import client.BuffStat;
import client.Skill;
import constants.game.GameConstants;
import constants.skills.Assassin;
import constants.skills.Bandit;
import constants.skills.Bowmaster;
import constants.skills.Buccaneer;
import constants.skills.ChiefBandit;
import constants.skills.Brawler;
import constants.skills.Cleric;
import constants.skills.Beginner;
import constants.skills.Marauder;
import constants.skills.DarkKnight;
import constants.skills.Paladin;
import constants.skills.ILMage;
import constants.skills.FPMage;
import constants.skills.ILWizard;
import constants.skills.FPWizard;
import constants.skills.Page;
import constants.skills.Hero;
import constants.skills.NightLord;
import constants.skills.Shadower;
import constants.skills.Bishop;
import constants.skills.ILArchMage;
import constants.skills.FPArchMage;
import constants.skills.Corsair;
import constants.skills.Crusader;
import constants.skills.DawnWarrior;
import constants.skills.DragonKnight;
import constants.skills.Fighter;
import constants.skills.GM;
import constants.skills.Marksman;
import constants.skills.NightWalker;
import constants.skills.Priest;
import constants.skills.Rogue;
import constants.skills.Spearman;
import constants.skills.SuperGM;
import constants.skills.ThunderBreaker;
import constants.skills.WhiteKnight;
import server.StatEffect;
import tools.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AgentCombatSkillClassifier {
    private static final Set<Integer> NON_DAMAGE_ACTIVE_SKILL_IDS = Set.of(
            Crusader.ARMOR_CRASH,
            WhiteKnight.MAGIC_CRASH,
            DragonKnight.POWER_CRASH
    );

    /**
     * Attack skills the server only accepts after a world precondition the Agent has to set up
     * itself (Meso Explosion: meso drops in the blast). Kept out of the automatic attack cache
     * until the AI performs the pre-action; explicit plans still execute.
     */
    private static final Set<Integer> PRE_ACTION_ATTACK_SKILL_IDS = Set.of(
            ChiefBandit.MESO_EXPLOSION
    );

    public static boolean requiresPreAction(int skillId) {
        return PRE_ACTION_ATTACK_SKILL_IDS.contains(skillId);
    }

    private static final Set<Integer> BUFF_BLACKLIST_SKILL_IDS = Set.of(
            Rogue.DARK_SIGHT,
            NightWalker.DARK_SIGHT
    );

    private static final Set<Integer> PARTY_SUPPORT_SKILL_IDS = Set.of(
            Assassin.HASTE,
            Bandit.HASTE,
            NightWalker.HASTE,
            Fighter.RAGE,
            DawnWarrior.RAGE,
            Cleric.BLESS,
            Priest.HOLY_SYMBOL,
            Spearman.HYPER_BODY,
            Buccaneer.PIRATES_RAGE,
            Buccaneer.SPEED_INFUSION,
            Corsair.SPEED_INFUSION,
            ThunderBreaker.SPEED_INFUSION,
            Bowmaster.SHARP_EYES,
            Marksman.SHARP_EYES,
            GM.HASTE,
            GM.BLESS,
            GM.HYPER_BODY,
            SuperGM.HASTE,
            SuperGM.HOLY_SYMBOL,
            SuperGM.HYPER_BODY
    );

    private AgentCombatSkillClassifier() {
    }

    public enum SkillCacheBucket {
        ACTIVE_HEAL,
        ACTIVE_ATTACK,
        SUMMON,
        SUPPORT_BUFF,
        /** Mob debuffs cast as special moves (Slow, Seal, Doom, Ninja Ambush, Crashes, Dispel, Monster Magnet). */
        DEBUFF,
        /** Self/party utility special moves with no buff stat to verify (Time Leap, Smokescreen, Chakra). */
        UTILITY,
        IGNORE
    }

    /** Attacks the generic offense test misses: fixed-damage Snipe and the cost-free energy attacks. */
    private static final Set<Integer> SPECIAL_ATTACK_SKILL_IDS = Set.of(
            Marksman.SNIPE, Marauder.ENERGY_BLAST, Marauder.ENERGY_DRAIN, Buccaneer.ENERGY_ORB,
            Beginner.THREE_SNAILS
    );

    /** Energy attacks: the client only allows them with a full Energy Charge bar. */
    private static final Set<Integer> ENERGY_ATTACK_SKILL_IDS = Set.of(
            Marauder.ENERGY_BLAST, Marauder.ENERGY_DRAIN, Buccaneer.ENERGY_ORB
    );

    public static boolean isEnergyAttackSkill(int skillId) {
        return ENERGY_ATTACK_SKILL_IDS.contains(skillId);
    }

    private static final Set<Integer> UTILITY_SPECIAL_MOVE_SKILL_IDS = Set.of(
            Buccaneer.TIME_LEAP, Shadower.SMOKE_SCREEN, ChiefBandit.CHAKRA, Brawler.MP_RECOVERY
    );

    public static boolean isUtilitySpecialMove(int skillId) {
        return UTILITY_SPECIAL_MOVE_SKILL_IDS.contains(skillId);
    }

    /** Keydown charge attacks whose WZ shape reads as an over-time buff; the server ignores the charge value. */
    private static final Set<Integer> CHARGE_ATTACK_SKILL_IDS = Set.of(
            FPArchMage.BIG_BANG, ILArchMage.BIG_BANG, Bishop.BIG_BANG
    );

    /** Attack packets that carry no damage of their own but apply a status to the targets. */
    private static final Set<Integer> STATUS_ATTACK_SKILL_IDS = Set.of(
            Shadower.TAUNT, NightLord.TAUNT, Rogue.DISORDER
    );

    /** Castable buffs with no WZ action node (the generic support test needs one). */
    private static final Set<Integer> EXPLICIT_SUPPORT_SKILL_IDS = Set.of(
            Hero.ENRAGE, NightLord.SHADOW_STARS, Marauder.TRANSFORMATION, Buccaneer.SUPER_TRANSFORMATION
    );

    /** Mirrors StatEffect.isMonsterBuff plus Monster Magnet: cast as a special move, applied to mobs in the skill box. */
    private static final Set<Integer> MOB_DEBUFF_SKILL_IDS = Set.of(
            Page.THREATEN, FPWizard.SLOW, ILWizard.SLOW, FPMage.SEAL, ILMage.SEAL, Priest.DOOM,
            NightLord.NINJA_AMBUSH, Shadower.NINJA_AMBUSH,
            Crusader.ARMOR_CRASH, DragonKnight.POWER_CRASH, WhiteKnight.MAGIC_CRASH,
            Priest.DISPEL, Hero.MONSTER_MAGNET, Paladin.MONSTER_MAGNET, DarkKnight.MONSTER_MAGNET,
            Corsair.HYPNOTIZE
    );

    public static boolean isMobDebuffSkill(int skillId) {
        return MOB_DEBUFF_SKILL_IDS.contains(skillId);
    }

    public static boolean isMonsterMagnet(int skillId) {
        return skillId == Hero.MONSTER_MAGNET || skillId == Paladin.MONSTER_MAGNET || skillId == DarkKnight.MONSTER_MAGNET;
    }

    public static SkillCacheBucket classifySkillCacheBucket(Skill skill, StatEffect effect) {
        if (skill == null) {
            return SkillCacheBucket.IGNORE;
        }
        if (isHealSkill(skill.getId())) {
            return isActiveHealSkill(skill, effect) ? SkillCacheBucket.ACTIVE_HEAL : SkillCacheBucket.IGNORE;
        }
        if (isActiveAttackSkill(skill, effect)) {
            return SkillCacheBucket.ACTIVE_ATTACK;
        }
        if (isSummonSkill(effect)) {
            return SkillCacheBucket.SUMMON;
        }
        if (isActiveSupportSkill(skill, effect) && !isBuffBlacklisted(skill.getId())) {
            return SkillCacheBucket.SUPPORT_BUFF;
        }
        if (isMobDebuffSkill(skill.getId())) {
            return SkillCacheBucket.DEBUFF;
        }
        if (isUtilitySpecialMove(skill.getId())) {
            return SkillCacheBucket.UTILITY;
        }
        return SkillCacheBucket.IGNORE;
    }

    public static boolean isPartySupportSkill(int skillId) {
        return PARTY_SUPPORT_SKILL_IDS.contains(skillId);
    }

    public static boolean isBuffBlacklisted(int skillId) {
        return BUFF_BLACKLIST_SKILL_IDS.contains(skillId);
    }

    /** Diagnostic: why {@link #classifySkillCacheBucket} returns IGNORE for this skill. */
    public static String ignoreReason(Skill skill, StatEffect effect) {
        if (skill == null || effect == null) {
            return "no skill/effect";
        }
        List<String> reasons = new ArrayList<>();
        if (NON_DAMAGE_ACTIVE_SKILL_IDS.contains(skill.getId())) reasons.add("listed non-damage active");
        if (effect.isOverTime()) reasons.add("overTime");
        if (!declaresOffense(effect)) reasons.add("no offense (damage=" + effect.hasDamage() + " matk=" + effect.hasMatk()
                + " mobCount=" + effect.getMobCount() + " box=" + effect.hasBoundingBox() + ")");
        if (skill.getSkillType() == 1 || skill.getSkillType() == 3) reasons.add("skillType=" + skill.getSkillType());
        if (effect.getMpCon() <= 0 && effect.getHpCon() <= 0 && !skill.isBeginnerSkill()) reasons.add("no cost");
        if (isSummonSkill(effect)) reasons.add("summon");
        if (isBuffBlacklisted(skill.getId())) reasons.add("buff blacklisted");
        if (effect.isOverTime() && (effect.getDuration() <= 0 || effect.getStatups().isEmpty())) {
            reasons.add("buff without duration/statups (duration=" + effect.getDuration() + " statups=" + effect.getStatups().size() + ")");
        }
        if (effect.isOverTime() && !skill.getAction() && skill.getSkillType() != 2) reasons.add("no action and skillType=" + skill.getSkillType());
        return String.join("; ", reasons);
    }

    public static boolean isActiveAttackSkill(Skill skill, StatEffect effect) {
        if (skill == null || effect == null) {
            return false;
        }
        if (NON_DAMAGE_ACTIVE_SKILL_IDS.contains(skill.getId())) {
            return false;
        }
        if (CHARGE_ATTACK_SKILL_IDS.contains(skill.getId()) || STATUS_ATTACK_SKILL_IDS.contains(skill.getId())
                || SPECIAL_ATTACK_SKILL_IDS.contains(skill.getId())) {
            return true;
        }
        if (effect.isOverTime() || !declaresOffense(effect)) {
            return false;
        }
        if (skill.getSkillType() == 1 || skill.getSkillType() == 3) {
            return false;
        }
        // v83 attack skills often omit a top-level action node; passive damage carriers do not
        // carry a client-paid cost. Use WZ skillType for explicit passives and cost as the fallback.
        return effect.getMpCon() > 0 || effect.getHpCon() > 0 || skill.isBeginnerSkill();
    }

    // Identifies skills the WZ source declares as offensive. Three WZ shapes cover every
    // attack skill we know of in v83; combined with isOverTime() this rejects utility skills.
    public static boolean declaresOffense(StatEffect effect) {
        return effect.hasDamage()
                || effect.hasMatk()
                || (effect.getMobCount() > 1 && effect.hasBoundingBox());
    }

    public static boolean isActiveSupportSkill(Skill skill, StatEffect effect) {
        if (skill == null || effect == null || !effect.isOverTime()) {
            return false;
        }
        if (effect.getDuration() <= 0 || effect.getStatups().isEmpty()) {
            return false;
        }
        if (isSummonSkill(effect)) {
            return false;
        }
        return skill.getAction() || skill.getSkillType() == 2 || EXPLICIT_SUPPORT_SKILL_IDS.contains(skill.getId());
    }

    public static boolean isCacheableSupportBuffSkill(Skill skill, StatEffect effect) {
        return isActiveSupportSkill(skill, effect) && !isBuffBlacklisted(skill.getId());
    }

    public static boolean isSummonSkill(StatEffect effect) {
        if (effect == null) {
            return false;
        }
        for (Pair<BuffStat, Integer> statup : effect.getStatups()) {
            BuffStat stat = statup.getLeft();
            if (stat == BuffStat.SUMMON || stat == BuffStat.PUPPET) {
                return true;
            }
        }
        return false;
    }

    public static boolean isActiveHealSkill(Skill skill, StatEffect effect) {
        return skill != null && effect != null && skill.getAction();
    }

    public static boolean isHealSkill(int skillId) {
        return skillId == Cleric.HEAL || skillId == SuperGM.HEAL_PLUS_DISPEL;
    }

    public static boolean shouldStopCacheScanAfterHealSkill(Skill skill) {
        return skill != null && isHealSkill(skill.getId());
    }

    public static int skillCacheSignature(Character bot) {
        int result = 1;
        for (Map.Entry<Skill, Character.SkillEntry> learned : bot.getSkills().entrySet()) {
            Skill skill = learned.getKey();
            if (skill == null) {
                continue;
            }
            result = 31 * result + skill.getId();
            result = 31 * result + bot.getSkillLevel(skill);
        }
        return result;
    }

    public static boolean shouldUseAsBestSingleTargetSkill(Character bot, Skill skill, StatEffect effect,
                                                           int attackCount, int bestAttackCount,
                                                           int bestPriority, int bestDamage,
                                                           int currentBestSkillId) {
        int priority = singleTargetSkillPriority(bot, skill);
        if (priority != bestPriority) {
            return priority > bestPriority;
        }

        int damage = effect != null ? effect.getDamagePercent() : 0;
        int score = damage * attackCount;
        int bestScore = bestDamage * bestAttackCount;
        if (score != bestScore) {
            return score > bestScore;
        }

        return currentBestSkillId == 0 || skill.getId() < currentBestSkillId;
    }

    public static long aoeSkillScore(StatEffect effect, int attackCount, int mobCount) {
        return (long) Math.max(0, effect.getDamagePercent())
                * Math.max(1, attackCount)
                * Math.max(1, mobCount);
    }

    public static int singleTargetSkillPriority(Character bot, Skill skill) {
        if (skill == null) {
            return Integer.MIN_VALUE;
        }
        if (skill.isBeginnerSkill()) {
            return 0;
        }
        return GameConstants.isInJobTree(skill.getId(), bot.getJob().getId()) ? 2 : 1;
    }

    public static List<Integer> cachedAttackSkillIds(List<Integer> attackSkillIds,
                                                     int attackSkillId,
                                                     int aoeSkillId) {
        if (attackSkillIds != null && !attackSkillIds.isEmpty()) {
            return attackSkillIds;
        }

        List<Integer> skillIds = new ArrayList<>(2);
        if (attackSkillId != 0) {
            skillIds.add(attackSkillId);
        }
        if (aoeSkillId != 0 && aoeSkillId != attackSkillId) {
            skillIds.add(aoeSkillId);
        }
        return skillIds;
    }
}
