package server.agents.capabilities.combat;

import client.Character;
import client.inventory.WeaponType;
import server.agents.capabilities.looting.AgentGrindLootTargetService;
import server.agents.capabilities.looting.AgentGrindLootStateRuntime;
import server.agents.capabilities.combat.AgentGrindTargetStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;
import server.agents.operations.events.AgentCombatPostureChangedEvent;
import server.agents.operations.events.AgentCombatPostureRuntime;

import java.awt.Point;

public final class AgentGrindModeTickService {
    private AgentGrindModeTickService() {
    }

    public record Result(boolean consumedTick, Point targetPos) {
    }

    public record Hooks(AgentGrindTargetSearchService.SearchHooks targetSearchHooks,
                        AgentGrindNoTargetFallbackService.Hooks noTargetFallbackHooks,
                        AgentGrindTargetCommitmentService.Hooks targetCommitmentHooks,
                        AgentGrindRangedEngagementService.Hooks rangedEngagementHooks,
                        AgentGrindNavigationTailService.Hooks navigationTailHooks,
                        int seekRange,
                        int lootRadius) {
    }

    public static Result tickGrindMode(AgentRuntimeEntry entry,
                                       Character agent,
                                       Point agentPosition,
                                       Point currentTargetPos,
                                       boolean runAiTick,
                                       Hooks hooks) {
        double seekRangeSq = (double) hooks.seekRange() * hooks.seekRange();
        long now = System.currentTimeMillis();
        AgentCombatLocalTargetLeaseRuntime.observePosition(entry, agent, agentPosition, now);
        Monster target = AgentGrindTargetStateRuntime.targetInSeekRangeOrCommitted(
                entry, agent, agentPosition, seekRangeSq, now);
        AgentAttackPlan attackPlan = target == null
                ? null
                : AgentCombatPlanRuntime.planAttack(entry, agent, target, AgentCombatConfig.cfg);

        AgentGrindLootTargetService.validateCachedGrindLootTarget(entry, agent);
        if (target == null && AgentGrindLootTargetService.preparePostKillLootBeforeTargetSearch(
                entry, agent, runAiTick, hooks.lootRadius(), now)) {
            if (!AgentGrindLootStateRuntime.hasGrindLootTarget(entry)) {
                return new Result(true, agentPosition);
            }
            AgentGrindNoTargetFallbackService.Result result =
                    AgentGrindNoTargetFallbackService.handleNoTarget(
                            entry, agent, agentPosition, currentTargetPos, runAiTick,
                            hooks.noTargetFallbackHooks());
            return new Result(result.consumedTick(), result.targetPos());
        }
        AgentGrindTargetSearchService.SearchResult searchResult =
                AgentGrindTargetSearchService.searchIfDue(
                        entry, agent, target, attackPlan, runAiTick, now, hooks.targetSearchHooks());
        target = searchResult.target();
        attackPlan = searchResult.attackPlan();

        AgentGrindLootTargetService.refreshGrindLootTarget(
                entry, agent, runAiTick, hooks.lootRadius(), target != null);
        if (AgentGrindLootStateRuntime.hasGrindLootTarget(entry)) {
            Point scheduledLootPosition = AgentGrindTargetPositionService.activeGrindLootPosition(
                    entry, agentPosition);
            if (scheduledLootPosition != null) {
                AgentCombatPostureRuntime.observe(entry, agent,
                        AgentCombatPostureChangedEvent.Posture.LOOTING, 0,
                        scheduledLootPosition, "collecting eligible post-kill loot", now);
                return new Result(false, scheduledLootPosition);
            }
        }
        Point immediateMeleeLootPosition =
                AgentGrindLootTargetService.immediateMeleeLootPosition(
                        entry, agent, agentPosition, hooks.lootRadius(), now);
        if (target == null) {
            AgentCombatPostureRuntime.observe(entry, agent,
                    AgentCombatPostureChangedEvent.Posture.SEARCHING, 0,
                    agentPosition, "no committed target; running local search policy", now);
            if (immediateMeleeLootPosition != null) {
                return new Result(false, immediateMeleeLootPosition);
            }
            AgentGrindNoTargetFallbackService.Result result =
                    AgentGrindNoTargetFallbackService.handleNoTarget(
                            entry, agent, agentPosition, currentTargetPos, runAiTick,
                            hooks.noTargetFallbackHooks());
            return new Result(result.consumedTick(), result.targetPos());
        }

        AgentGrindTargetCommitmentService.Result commitment =
                AgentGrindTargetCommitmentService.commitTarget(
                        entry, agent, agentPosition, target, attackPlan, now,
                        hooks.targetCommitmentHooks());
        target = commitment.target();
        Point targetPosition = commitment.targetPosition();
        attackPlan = commitment.attackPlan();
        Monster rangedPriorityTarget = commitment.rangedPriorityTarget();
        if (attackPlan == null) {
            attackPlan = AgentCombatPlanRuntime.planAttack(entry, agent, target, AgentCombatConfig.cfg);
        }

        AgentGrindRangedEngagementService.Result engagement =
                AgentGrindRangedEngagementService.engage(
                        entry, agent, agentPosition, currentTargetPos, target, targetPosition, attackPlan,
                        rangedPriorityTarget, hooks.rangedEngagementHooks());
        WeaponType grindWeaponType = engagement.weaponType();
        AgentAttackRoute grindAttackRoute = attackPlan != null ? attackPlan.route : AgentAttackRoute.CLOSE;
        boolean shouldRetreatForRangedSpacing = engagement.shouldRetreatForRangedSpacing();
        Point crossRegionRetreatPos = engagement.crossRegionRetreatPos();
        Point aoeRepositionPos = engagement.aoeRepositionPos();
        if (engagement.consumedTick()) {
            return new Result(true, engagement.targetPos());
        }

        Point targetPos = AgentGrindNavigationTailService.resolveNavigationTarget(
                entry,
                agentPosition,
                targetPosition,
                grindWeaponType,
                grindAttackRoute,
                crossRegionRetreatPos,
                aoeRepositionPos,
                shouldRetreatForRangedSpacing,
                engagement.attackAttemptedInRange(),
                hooks.navigationTailHooks());
        if (immediateMeleeLootPosition != null) {
            targetPos = immediateMeleeLootPosition;
        }
        return new Result(false, targetPos);
    }
}
