package server.agents.capabilities.combat;

import client.inventory.WeaponType;
import server.agents.integration.AgentDegenerateAttackStateRuntime;
import server.agents.integration.AgentPatrolStateRuntime;
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
        Point select(AgentRuntimeEntry entry, Point agentPosition, Point combatTargetPosition, boolean retreatChecked);
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
                                                Point crossRegionRetreatPos,
                                                Point aoeRepositionPos,
                                                boolean shouldRetreatForRangedSpacing,
                                                Hooks hooks) {
        Point targetPos = crossRegionRetreatPos != null
                ? crossRegionRetreatPos
                : aoeRepositionPos != null
                ? hooks.navigationTargetSelector().select(entry, agentPosition, aoeRepositionPos, false)
                : hooks.navigationTargetSelector().select(
                        entry, agentPosition, mobPosition, shouldRetreatForRangedSpacing);

        if (AgentDegenerateAttackStateRuntime.degenAttackDone(entry)
                && !hooks.retreatPolicy().shouldRetreat(weaponType, agentPosition, mobPosition)) {
            AgentDegenerateAttackStateRuntime.clear(entry);
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
