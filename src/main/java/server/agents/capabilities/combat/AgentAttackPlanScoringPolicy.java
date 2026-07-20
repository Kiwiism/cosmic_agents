package server.agents.capabilities.combat;

import client.Character;
import java.util.ArrayList;
import java.util.List;
import server.combat.CombatFormulaProvider;
import server.life.Monster;

public final class AgentAttackPlanScoringPolicy {
    public record AgentAttackPlanScore<T extends AgentAttackPlan>(
            T plan,
            double usefulDamage,
            double rawDamage,
            double usefulDps,
            double rawDps,
            boolean minimumKillsFullHpTargets) {
    }

    private AgentAttackPlanScoringPolicy() {
    }

    public static <T extends AgentAttackPlan> T selectBestAttackPlan(Character agent, List<T> candidates) {
        List<AgentAttackPlanScore<T>> scores = new ArrayList<>(candidates.size());
        for (T candidate : candidates) {
            scores.add(scoreAttackPlan(agent, candidate));
        }

        boolean hasGuaranteedFullHpKill = scores.stream().anyMatch(AgentAttackPlanScore::minimumKillsFullHpTargets);
        AgentAttackPlanScore<T> best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (AgentAttackPlanScore<T> score : scores) {
            if (hasGuaranteedFullHpKill && !score.minimumKillsFullHpTargets) {
                continue;
            }
            double candidateScore = hasGuaranteedFullHpKill ? score.usefulDps : score.rawDps;
            if (best == null
                    || candidateScore > bestScore
                    || (Double.compare(candidateScore, bestScore) == 0
                    && AgentAttackPlanTieBreakPolicy.isBetter(
                    score.plan.cooldownMs, score.plan.skillId, best.plan.cooldownMs, best.plan.skillId))) {
                best = score;
                bestScore = candidateScore;
            }
        }
        return best != null ? best.plan : null;
    }

    public static <T extends AgentAttackPlan> AgentAttackPlanScore<T> scoreAttackPlan(Character agent, T attackPlan) {
        CombatFormulaProvider.DamageProfile damageProfile = AgentAttackDamageProfileService.resolve(agent, attackPlan);
        double usefulDamage = 0.0d;
        double rawDamage = 0.0d;
        boolean minimumKillsFullHpTargets = !attackPlan.targets.isEmpty();
        for (Monster target : attackPlan.targets) {
            double expectedDamage = CombatFormulaProvider.getInstance().estimateExpectedDamage(
                    agent, target, attackPlan.numDamage, attackPlan.skillId, damageProfile);
            usefulDamage += AgentCombatScoringPolicy.capDamageByCurrentHp(expectedDamage, target.getHp());
            rawDamage += expectedDamage;

            int fullHp = target.getMaxHp();
            if (fullHp <= 0) {
                fullHp = target.getHp();
            }
            int minimumDamage = CombatFormulaProvider.getInstance().estimateMinimumDamage(
                    agent, target, attackPlan.numDamage, attackPlan.skillId, damageProfile);
            if (fullHp <= 0 || minimumDamage < fullHp) {
                minimumKillsFullHpTargets = false;
            }
        }
        double animationSeconds = Math.max(1, attackPlan.cooldownMs) / 1000.0d;
        return new AgentAttackPlanScore<>(attackPlan, usefulDamage, rawDamage,
                usefulDamage / animationSeconds, rawDamage / animationSeconds, minimumKillsFullHpTargets);
    }
}
