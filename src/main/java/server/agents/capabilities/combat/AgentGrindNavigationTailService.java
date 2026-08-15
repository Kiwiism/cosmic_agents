package server.agents.capabilities.combat;

import client.inventory.WeaponType;
import server.agents.capabilities.movement.AgentPatrolStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

public final class AgentGrindNavigationTailService {
    private AgentGrindNavigationTailService() {
    }

    public record Hooks(NavigationTargetSelector navigationTargetSelector,
                        RetreatPolicy retreatPolicy,
                        ConvenientLootTargetSelector convenientLootTargetSelector) {
    }

    @FunctionalInterface
    public interface NavigationTargetSelector {
        Point select(AgentRuntimeEntry entry,
                     Point agentPosition,
                     Point combatTargetPosition,
                     WeaponType weaponType,
                     AgentAttackRoute attackRoute,
                     boolean retreatChecked);
    }

    @FunctionalInterface
    public interface RetreatPolicy {
        boolean shouldRetreat(WeaponType weaponType, Point agentPosition, Point targetPosition);
    }

    @FunctionalInterface
    public interface ConvenientLootTargetSelector {
        Point select(AgentRuntimeEntry entry, Point agentPosition, Point mobPosition);
    }

    public static Point resolveNavigationTarget(AgentRuntimeEntry entry,
                                                Point agentPosition,
                                                Point mobPosition,
                                                WeaponType weaponType,
                                                AgentAttackRoute attackRoute,
                                                Point crossRegionRetreatPos,
                                                Point aoeRepositionPos,
                                                boolean shouldRetreatForRangedSpacing,
                                                boolean attackAttemptedInRange,
                                                Hooks hooks) {
        Point targetPos = crossRegionRetreatPos != null
                ? crossRegionRetreatPos
                : aoeRepositionPos != null
                ? hooks.navigationTargetSelector().select(
                        entry, agentPosition, aoeRepositionPos, weaponType, attackRoute, true)
                : hooks.navigationTargetSelector().select(
                        entry, agentPosition, mobPosition, weaponType, attackRoute,
                        shouldRetreatForRangedSpacing || attackAttemptedInRange);

        boolean degenerateAttackDone = AgentDegenerateAttackStateRuntime.degenAttackDone(entry);
        boolean noUsableRetreat = degenerateAttackDone
                && hooks.retreatPolicy().shouldRetreat(weaponType, agentPosition, mobPosition)
                && targetPos.equals(mobPosition);
        if (degenerateAttackDone
                && (!hooks.retreatPolicy().shouldRetreat(weaponType, agentPosition, mobPosition)
                || noUsableRetreat)) {
            AgentDegenerateAttackStateRuntime.clear(entry);
            if (noUsableRetreat) {
                targetPos = agentPosition;
            }
        }

        if (crossRegionRetreatPos == null && !shouldRetreatForRangedSpacing
                && aoeRepositionPos == null && !AgentPatrolStateRuntime.hasPatrolRegion(entry)) {
            Point lootPos = hooks.convenientLootTargetSelector().select(entry, agentPosition, mobPosition);
            if (lootPos != null) {
                targetPos = lootPos;
            }
        }
        return targetPos;
    }
}
