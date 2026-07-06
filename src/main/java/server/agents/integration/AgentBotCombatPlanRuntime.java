package server.agents.integration;

import client.Character;
import server.agents.capabilities.combat.AgentAttackPlan;
import server.agents.capabilities.combat.AgentAttackPlanScoringPolicy;
import server.agents.capabilities.combat.AgentBasicAttackPlanRuntime;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentCombatSkillClassifier;
import server.agents.capabilities.combat.AgentSkillAttackPlanRuntime;
import server.agents.runtime.AgentPerformanceMonitor;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.util.ArrayList;
import java.util.List;

public final class AgentBotCombatPlanRuntime {
    private AgentBotCombatPlanRuntime() {
    }

    public static AgentAttackPlan planAttack(AgentRuntimeEntry entry, Character bot, Monster target,
                                             AgentCombatConfig.Config config) {
        long startedAt = System.nanoTime();
        try {
            List<AgentAttackPlan> candidates = new ArrayList<>(3);

            for (int skillId : AgentCombatSkillClassifier.cachedAttackSkillIds(
                    AgentBotCombatSkillCacheStateRuntime.attackSkillIds(entry),
                    AgentBotCombatSkillCacheStateRuntime.attackSkillId(entry),
                    AgentBotCombatSkillCacheStateRuntime.aoeSkillId(entry))) {
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
