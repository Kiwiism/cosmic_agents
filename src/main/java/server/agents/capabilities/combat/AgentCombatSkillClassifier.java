package server.agents.capabilities.combat;

import client.Character;
import client.BuffStat;
import client.Skill;
import constants.game.GameConstants;
import constants.skills.Assassin;
import constants.skills.Bandit;
import constants.skills.Bowmaster;
import constants.skills.Buccaneer;
import constants.skills.Cleric;
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
        IGNORE
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
        return SkillCacheBucket.IGNORE;
    }

    public static boolean isPartySupportSkill(int skillId) {
        return PARTY_SUPPORT_SKILL_IDS.contains(skillId);
    }

    public static boolean isBuffBlacklisted(int skillId) {
        return BUFF_BLACKLIST_SKILL_IDS.contains(skillId);
    }

    public static boolean isActiveAttackSkill(Skill skill, StatEffect effect) {
        if (skill == null || effect == null) {
            return false;
        }
        if (NON_DAMAGE_ACTIVE_SKILL_IDS.contains(skill.getId())) {
            return false;
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
        return skill.getAction() || skill.getSkillType() == 2;
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
