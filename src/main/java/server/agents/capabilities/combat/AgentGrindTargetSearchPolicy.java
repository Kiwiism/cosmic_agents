package server.agents.capabilities.combat;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

/**
 * Agent-owned grind target search and searched-target adoption policy.
 */
public final class AgentGrindTargetSearchPolicy {
    private AgentGrindTargetSearchPolicy() {
    }

    public static boolean shouldSearchForGrindTarget(AgentRuntimeEntry entry,
                                                     Character agent,
                                                     Monster currentTarget,
                                                     AgentAttackPlan currentAttackPlan,
                                                     long now) {
        boolean currentTargetReachable = agent != null && currentTarget != null
                && AgentCombatTargetRuntime.isReachableGrindTarget(entry, agent, currentTarget);
        return shouldSearchForGrindTarget(
                entry, agent, currentTarget, currentAttackPlan, now, currentTargetReachable);
    }

    static boolean shouldSearchForGrindTarget(AgentRuntimeEntry entry,
                                              Character agent,
                                              Monster currentTarget,
                                              AgentAttackPlan currentAttackPlan,
                                              long now,
                                              boolean currentTargetReachable) {
        if (entry == null) {
            return false;
        }
        if (currentTarget == null) {
            return true;
        }
        if (AgentGrindSearchStateRuntime.searchBlocked(entry, now)) {
            return false;
        }
        if (agent == null) {
            return true;
        }
        if (currentAttackPlan == null) {
            if (AgentGrindTargetStateRuntime.committedTo(entry, currentTarget, now)) {
                return false;
            }
            return !currentTargetReachable;
        }
        if (!AgentCombatRangePolicy.isTargetInAttackRange(currentAttackPlan, agent, currentTarget)) {
            if (AgentGrindTargetStateRuntime.committedTo(entry, currentTarget, now)) {
                return false;
            }
            return !currentTargetReachable;
        }
        // In range we normally stay committed (avoids flip-flop). Exception: an AoE bot stuck
        // single-targeting keeps scanning for a better cluster; the switch itself is gated by
        // cluster-size hysteresis in shouldSwitchToSearchedTarget.
        return AgentCombatScoringPolicy.isAoeSingleTargeting(
                currentAttackPlan.skillId,
                currentAttackPlan.targets.size(),
                AgentCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                AgentCombatSkillCacheStateRuntime.aoeSkillId(entry),
                AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
    }

    /**
     * Decide whether to adopt a freshly searched grind target over the current one. Always adopts
     * when not committed (current null, no plan, or current out of attack range). When committed to
     * an in-range target, only switches if the searched target anchors a strictly larger AoE cluster
     * to prevent flip-flop between near-equal targets.
     */
    public static boolean shouldSwitchToSearchedTarget(AgentRuntimeEntry entry,
                                                       Character agent,
                                                       Monster current,
                                                       Monster searched,
                                                       AgentAttackPlan currentPlan) {
        if (searched == null || searched == current) {
            return false;
        }
        if (current == null || agent == null || currentPlan == null
                || !AgentCombatRangePolicy.isTargetInAttackRange(currentPlan, agent, current)) {
            return true;
        }
        int searchedClusterSize = agent.getMap() == null || searched.getPosition() == null
                ? 0
                : AgentCombatScoringPolicy.legacyCappedAoeClusterSize(
                        searched,
                        agent.getMap().getAllMonsters(),
                        AgentCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                        AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
        int currentClusterSize = agent.getMap() == null || current.getPosition() == null
                ? 0
                : AgentCombatScoringPolicy.legacyCappedAoeClusterSize(
                        current,
                        agent.getMap().getAllMonsters(),
                        AgentCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                        AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
        return searchedClusterSize > currentClusterSize;
    }
}
