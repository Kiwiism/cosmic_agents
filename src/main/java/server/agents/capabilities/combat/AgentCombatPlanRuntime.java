package server.agents.capabilities.combat;

import client.Character;
import client.Skill;
import client.inventory.WeaponType;
import server.StatEffect;
import server.agents.integration.AgentSkillGatewayRuntime;
import server.agents.monitoring.AgentPerformanceMonitor;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AgentCombatPlanRuntime {
    private AgentCombatPlanRuntime() {
    }

    public static AgentAttackPlan planAttack(AgentRuntimeEntry entry, Character bot, Monster target,
                                             AgentCombatConfig.Config config) {
        long startedAt = System.nanoTime();
        try {
            List<AgentAttackPlan> candidates = new ArrayList<>(3);

            List<Integer> attackSkillIds = AgentCombatSkillClassifier.cachedAttackSkillIds(
                    AgentCombatSkillCacheStateRuntime.attackSkillIds(entry),
                    AgentCombatSkillCacheStateRuntime.attackSkillId(entry),
                    AgentCombatSkillCacheStateRuntime.aoeSkillId(entry));
            int requiredSkillId = entry.capabilityStates()
                    .require(AgentCombatSkillConstraintState.STATE_KEY).requiredSkillId();
            if (requiredSkillId > 0) {
                attackSkillIds = attackSkillIds.stream()
                        .filter(skillId -> skillId == requiredSkillId)
                        .toList();
            }
            for (int skillId : attackSkillIds) {
                AgentAttackPlan skillAttack = AgentSkillAttackPlanRuntime.planSkillAttack(bot, target, skillId, config);
                skillAttack = AgentCombatObjectiveTargetStateRuntime.restrictAttackPlan(entry, skillAttack);
                if (skillAttack != null) {
                    candidates.add(skillAttack);
                }
            }

            // A wand swing is the emergency fallback for a magician, not a competing damage plan. Allowing
            // it into the score alongside a usable spell can select an invisible-looking basic hit at melee
            // range instead of broadcasting the magic-skill packet.
            if (!hasUsableMagicSkill(candidates) && !hasLearnedMagicSkill(bot, attackSkillIds)) {
                AgentAttackPlan basicAttack = AgentBasicAttackPlanRuntime.planBasicAttack(bot, target);
                basicAttack = AgentCombatObjectiveTargetStateRuntime.restrictAttackPlan(entry, basicAttack);
                if (basicAttack != null) {
                    candidates.add(basicAttack);
                }
            }
            WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
            return AgentAttackPlanScoringPolicy.selectBestAttackPlan(
                    bot, preferRangedBossCandidates(target, weaponType, candidates));
        } finally {
            AgentPerformanceMonitor.record("combat-plan", System.nanoTime() - startedAt);
        }
    }

    static boolean hasUsableMagicSkill(List<AgentAttackPlan> candidates) {
        return candidates.stream().anyMatch(candidate -> candidate.skillId > 0
                && candidate.route == AgentAttackRoute.MAGIC);
    }

    static List<AgentAttackPlan> preferRangedBossCandidates(Monster target,
                                                             WeaponType weaponType,
                                                             List<AgentAttackPlan> candidates) {
        if (target == null || !target.isBoss()
                || !AgentAttackExecutionProvider.isDegenerateCapableRangedWeapon(weaponType)) {
            return candidates;
        }
        List<AgentAttackPlan> rangedCandidates = candidates.stream()
                .filter(candidate -> candidate.route == AgentAttackRoute.RANGED)
                .toList();
        return rangedCandidates.isEmpty() ? candidates : rangedCandidates;
    }

    private static boolean hasLearnedMagicSkill(Character bot, List<Integer> skillIds) {
        for (int skillId : skillIds) {
            Skill skill = AgentSkillGatewayRuntime.skills().getSkill(skillId);
            if (skill != null
                    && bot.getSkillLevel(skill) > 0
                    && AgentAttackExecutionProvider.determineSkillRoute(bot, skillId)
                    == AgentAttackRoute.MAGIC) {
                return true;
            }
        }
        // The per-Agent attack cache is deliberately lazy and may briefly be stale after advancement,
        // skill allocation, or rematerialization. Inspect the character's authoritative learned-skill
        // snapshot as a fallback so those windows cannot turn a magician into a basic wand attacker.
        return hasLearnedOffensiveMagicSkill(bot, bot.getSkills());
    }

    static boolean hasLearnedOffensiveMagicSkill(Character bot,
                                                   Map<Skill, Character.SkillEntry> learnedSkills) {
        for (Map.Entry<Skill, Character.SkillEntry> learned : learnedSkills.entrySet()) {
            Skill skill = learned.getKey();
            Character.SkillEntry entry = learned.getValue();
            int skillLevel = entry != null ? Byte.toUnsignedInt(entry.skillevel) : 0;
            if (skill == null || skillLevel <= 0 || skillLevel > skill.getMaxLevel()) {
                continue;
            }
            StatEffect effect = skill.getEffect(skillLevel);
            if (AgentCombatSkillClassifier.isActiveAttackSkill(skill, effect)
                    && AgentAttackExecutionProvider.determineSkillRoute(bot, skill.getId())
                    == AgentAttackRoute.MAGIC) {
                return true;
            }
        }
        return false;
    }
}
