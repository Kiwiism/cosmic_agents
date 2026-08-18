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
        // Treat the retarget gate as the cadence for expensive live route validation too. The
        // previous order ran a full reliability-aware A* on every 50 ms movement tick and only
        // afterwards discovered that the 400 ms search gate was still closed.
        if (entry == null) {
            return false;
        }
        if (currentTarget != null && AgentGrindSearchStateRuntime.searchBlocked(entry, now)) {
            return false;
        }
        // An already attackable target is reachable by definition. Do not let a cold/missing
        // navigation graph turn a valid in-range combat decision into an unnecessary retarget.
        boolean currentTargetAttackable = agent != null && currentTarget != null
                && currentAttackPlan != null
                && AgentCombatRangePolicy.isTargetInAttackRange(
                        currentAttackPlan, agent, currentTarget);
        boolean currentTargetReachable = currentTargetAttackable
                || (agent != null && currentTarget != null
                && AgentCombatTargetRuntime.isReachableGrindTarget(entry, agent, currentTarget));
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
        // Reachability is a correctness gate, not a target-preference signal. A commitment may
        // damp ordinary target churn, but it must never retain a target whose live route has
        // become unavailable or suppressed.
        if (!currentTargetReachable) {
            return true;
        }
        // A commitment suppresses equivalent remote-target churn, but a genuinely local
        // required opportunity is a different class of decision and may preempt immediately.
        if (AgentCombatTargetRuntime.hasBetterLocalPreferredOpportunity(
                entry, agent, currentTarget)) {
            return true;
        }
        if (currentAttackPlan == null) {
            return !AgentGrindTargetStateRuntime.committedTo(entry, currentTarget, now);
        }
        if (!AgentCombatRangePolicy.isTargetInAttackRange(currentAttackPlan, agent, currentTarget)) {
            return !AgentGrindTargetStateRuntime.committedTo(entry, currentTarget, now);
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
                : AgentCombatScoringPolicy.cappedAoeClusterSize(
                        searched,
                        server.agents.perception.AgentMapPerception.monsters(agent.getMap()),
                        AgentCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                        AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
        int currentClusterSize = agent.getMap() == null || current.getPosition() == null
                ? 0
                : AgentCombatScoringPolicy.cappedAoeClusterSize(
                        current,
                        server.agents.perception.AgentMapPerception.monsters(agent.getMap()),
                        AgentCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                        AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
        return searchedClusterSize > currentClusterSize;
    }

    static boolean shouldPreemptCommittedTarget(boolean currentPreferred,
                                                int currentLocalityClass,
                                                int candidateLocalityClass) {
        return candidateLocalityClass < currentLocalityClass
                || (!currentPreferred && candidateLocalityClass <= 1);
    }
}
