package server.agents.capabilities.combat;

import client.Character;
import server.agents.monitoring.AgentPerformanceMonitor;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.util.ArrayList;
import java.util.List;

public final class AgentCombatPlanRuntime {
    private AgentCombatPlanRuntime() {
    }

    public static AgentAttackPlan planAttack(AgentRuntimeEntry entry, Character bot, Monster target,
                                             AgentCombatConfig.Config config) {
        long startedAt = System.nanoTime();
        try {
            List<AgentAttackPlan> candidates = new ArrayList<>(3);

            for (int skillId : AgentCombatSkillClassifier.cachedAttackSkillIds(
                    AgentCombatSkillCacheStateRuntime.attackSkillIds(entry),
                    AgentCombatSkillCacheStateRuntime.attackSkillId(entry),
                    AgentCombatSkillCacheStateRuntime.aoeSkillId(entry))) {
                AgentAttackPlan skillAttack = AgentSkillAttackPlanRuntime.planSkillAttack(bot, target, skillId, config);
                skillAttack = AgentCombatObjectiveTargetStateRuntime.restrictAttackPlan(entry, skillAttack);
                if (skillAttack != null) {
                    candidates.add(skillAttack);
                }
            }

            // A wand swing is the emergency fallback for a magician, not a competing damage plan. Allowing
            // it into the score alongside a usable spell can select an invisible-looking basic hit at melee
            // range instead of broadcasting the magic-skill packet.
            if (!hasUsableMagicSkill(candidates)) {
                AgentAttackPlan basicAttack = AgentBasicAttackPlanRuntime.planBasicAttack(bot, target);
                basicAttack = AgentCombatObjectiveTargetStateRuntime.restrictAttackPlan(entry, basicAttack);
                if (basicAttack != null) {
                    candidates.add(basicAttack);
                }
            }
            return AgentAttackPlanScoringPolicy.selectBestAttackPlan(bot, candidates);
        } finally {
            AgentPerformanceMonitor.record("combat-plan", System.nanoTime() - startedAt);
        }
    }

    static boolean hasUsableMagicSkill(List<AgentAttackPlan> candidates) {
        return candidates.stream().anyMatch(candidate -> candidate.skillId > 0
                && candidate.route == AgentAttackRoute.MAGIC);
    }
}
