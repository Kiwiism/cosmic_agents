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
                if (skillAttack != null) {
                    candidates.add(skillAttack);
                }
            }

            AgentAttackPlan basicAttack = AgentBasicAttackPlanRuntime.planBasicAttack(bot, target);
            if (basicAttack != null) {
                candidates.add(basicAttack);
            }
            return AgentAttackPlanScoringPolicy.selectBestAttackPlan(bot, candidates);
        } finally {
            AgentPerformanceMonitor.record("combat-plan", System.nanoTime() - startedAt);
        }
    }
}
