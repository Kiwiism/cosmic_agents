package server.agents.capabilities.combat;

import client.Character;
import client.inventory.WeaponType;
import server.agents.capabilities.looting.AgentGrindLootTargetService;
import server.agents.integration.AgentBotCombatPlanRuntime;
import server.agents.integration.AgentBotGrindTargetStateRuntime;
import server.agents.runtime.AgentGrindNoTargetFallbackService;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

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
        Monster target = AgentBotGrindTargetStateRuntime.targetInSeekRange(
                entry, agent, agentPosition, seekRangeSq);
        long now = System.currentTimeMillis();
        AgentAttackPlan attackPlan = target == null
                ? null
                : AgentBotCombatPlanRuntime.planAttack(entry, agent, target, AgentCombatConfig.cfg);

        AgentGrindLootTargetService.validateCachedGrindLootTarget(entry, agent);
        AgentGrindTargetSearchService.SearchResult searchResult =
                AgentGrindTargetSearchService.searchIfDue(
                        entry, agent, target, attackPlan, runAiTick, now, hooks.targetSearchHooks());
        target = searchResult.target();
        attackPlan = searchResult.attackPlan();

        AgentGrindLootTargetService.refreshGrindLootTarget(entry, agent, runAiTick, hooks.lootRadius());
        if (target == null) {
            AgentGrindNoTargetFallbackService.Result result =
                    AgentGrindNoTargetFallbackService.handleNoTarget(
                            entry, agent, agentPosition, currentTargetPos, runAiTick,
                            hooks.noTargetFallbackHooks());
            return new Result(result.consumedTick(), result.targetPos());
        }

        AgentGrindTargetCommitmentService.Result commitment =
                AgentGrindTargetCommitmentService.commitTarget(
                        entry, agent, agentPosition, target, attackPlan, hooks.targetCommitmentHooks());
        target = commitment.target();
        Point targetPosition = commitment.targetPosition();
        attackPlan = commitment.attackPlan();
        Monster rangedPriorityTarget = commitment.rangedPriorityTarget();
        if (attackPlan == null) {
            attackPlan = AgentBotCombatPlanRuntime.planAttack(entry, agent, target, AgentCombatConfig.cfg);
        }

        AgentGrindRangedEngagementService.Result engagement =
                AgentGrindRangedEngagementService.engage(
                        entry, agent, agentPosition, currentTargetPos, target, targetPosition, attackPlan,
                        rangedPriorityTarget, hooks.rangedEngagementHooks());
        WeaponType grindWeaponType = engagement.weaponType();
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
                crossRegionRetreatPos,
                aoeRepositionPos,
                shouldRetreatForRangedSpacing,
                hooks.navigationTailHooks());
        return new Result(false, targetPos);
    }
}
