package client;

import constants.game.GameConstants;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Shared final-execution eligibility policy for players and Agents. */
public final class SkillEligibilityPolicy {
    private SkillEligibilityPolicy() {
    }

    public static Result evaluate(Character character,
                                  Skill skill,
                                  int claimedSkillLevel,
                                  boolean allowUnlearnedEventSkill,
                                  BooleanSupplier weaponAndAmmoRequirements) {
        if (character == null || skill == null) {
            return Result.rejected(Rejection.UNKNOWN_SKILL);
        }
        int skillId = skill.getId();
        if (character.isPartnerSessionBorrowedSkill(skillId)) {
            return Result.rejected(Rejection.PARTNER_BORROWED_SKILL);
        }
        int learnedLevel = character.getSkillLevel(skill);
        if (!allowUnlearnedEventSkill && (learnedLevel <= 0 || learnedLevel != claimedSkillLevel)) {
            return Result.rejected(Rejection.NOT_LEARNED_AT_LEVEL);
        }
        if (!character.isGM() && GameConstants.isGMSkills(skillId)) {
            return Result.rejected(Rejection.GM_PERMISSION);
        }
        if (!character.isGM() && !skill.isBeginnerSkill()
                && !GameConstants.isInJobTree(skillId, character.getJob().getId())) {
            return Result.rejected(Rejection.WRONG_JOB_TREE);
        }
        if (GameConstants.isPqSkill(skillId) && !GameConstants.isPqSkillMap(character.getMapId())) {
            return Result.rejected(Rejection.EVENT_PERMISSION);
        }
        if (character.skillIsCooling(skillId)) {
            return Result.rejected(Rejection.COOLDOWN);
        }
        int executionLevel = allowUnlearnedEventSkill ? claimedSkillLevel : learnedLevel;
        if (executionLevel <= 0 || skill.getEffect(executionLevel) == null
                || !skill.getEffect(executionLevel).canPaySkillCost(character)) {
            return Result.rejected(Rejection.HP_MP_COST);
        }
        BooleanSupplier requirements = Objects.requireNonNullElse(weaponAndAmmoRequirements, () -> true);
        if (!requirements.getAsBoolean()) {
            return Result.rejected(Rejection.WEAPON_OR_AMMO);
        }
        return Result.allowed(executionLevel);
    }

    public static boolean isLearnedAndAllowedJob(Character character, Skill skill) {
        if (character == null || skill == null || character.getSkillLevel(skill) <= 0) {
            return false;
        }
        if (character.isPartnerSessionBorrowedSkill(skill.getId())) {
            return false;
        }
        if (!character.isGM() && GameConstants.isGMSkills(skill.getId())) {
            return false;
        }
        return character.isGM() || skill.isBeginnerSkill()
                || GameConstants.isInJobTree(skill.getId(), character.getJob().getId());
    }

    public enum Rejection {
        UNKNOWN_SKILL,
        NOT_LEARNED_AT_LEVEL,
        WRONG_JOB_TREE,
        GM_PERMISSION,
        EVENT_PERMISSION,
        COOLDOWN,
        HP_MP_COST,
        WEAPON_OR_AMMO,
        PARTNER_BORROWED_SKILL
    }

    public record Result(boolean allowed, int executionLevel, Rejection rejection) {
        private static Result allowed(int executionLevel) {
            return new Result(true, executionLevel, null);
        }

        private static Result rejected(Rejection rejection) {
            return new Result(false, 0, rejection);
        }
    }
}
