package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;

import server.agents.capabilities.combat.AgentGrindNavigationTargetSelector;
import server.agents.capabilities.navigation.AgentNavigationPathService;
import server.agents.capabilities.navigation.AgentNavigationRegionService;

import java.awt.Point;

/**
 * Temporary Agent-owned runtime bridge for grind retreat/navigation targeting.
 */
public final class AgentGrindNavigationRuntime {
    private AgentGrindNavigationRuntime() {
    }

    public static Point selectGrindNavigationTarget(AgentRuntimeEntry entry, Point agentPosition, Point combatTargetPosition) {
        return AgentGrindNavigationTargetSelector.selectGrindNavigationTarget(
                entry, agentPosition, combatTargetPosition, hooks());
    }

    public static Point selectGrindNavigationTarget(AgentRuntimeEntry entry,
                                                    Point agentPosition,
                                                    Point combatTargetPosition,
                                                    boolean crossRegionRetreatChecked) {
        return AgentGrindNavigationTargetSelector.selectGrindNavigationTarget(
                entry, agentPosition, combatTargetPosition, crossRegionRetreatChecked, hooks());
    }

    public static Point selectCrossRegionRetreatTarget(AgentRuntimeEntry entry,
                                                       Point agentPosition,
                                                       Point combatTargetPosition) {
        return AgentGrindNavigationTargetSelector.selectCrossRegionRetreatTarget(
                entry, agentPosition, combatTargetPosition, hooks());
    }

    public static boolean shouldUseLocalCombatRetreatTarget(AgentRuntimeEntry entry,
                                                            Point agentPosition,
                                                            Point combatTargetPosition,
                                                            Point retreatPosition) {
        return AgentGrindNavigationTargetSelector.shouldUseLocalCombatRetreatTarget(
                entry, agentPosition, combatTargetPosition, retreatPosition, hooks());
    }

    private static AgentGrindNavigationTargetSelector.NavigationHooks hooks() {
        return new AgentGrindNavigationTargetSelector.NavigationHooks(
                AgentNavigationRegionService::resolveCurrentRegionId,
                AgentNavigationRegionService::resolveTargetRegionId,
                AgentNavigationPathService::findPath,
                AgentMovementPhysicsConfig.configuredGrindEdgeMargin(),
                AgentMovementPhysicsConfig.configuredJumpYThreshold());
    }
}
