package server.agents.runtime;

import server.agents.capabilities.combat.AgentGrindNavigationTargetSelector;
import server.bots.BotEntry;
import server.bots.BotMovementManager;
import server.bots.BotNavigationManager;

import java.awt.Point;

/**
 * Temporary Agent-owned runtime bridge for grind retreat/navigation targeting.
 */
public final class AgentGrindNavigationRuntime {
    private AgentGrindNavigationRuntime() {
    }

    public static Point selectGrindNavigationTarget(BotEntry entry, Point agentPosition, Point combatTargetPosition) {
        return AgentGrindNavigationTargetSelector.selectGrindNavigationTarget(
                entry, agentPosition, combatTargetPosition, hooks());
    }

    public static Point selectGrindNavigationTarget(BotEntry entry,
                                                    Point agentPosition,
                                                    Point combatTargetPosition,
                                                    boolean crossRegionRetreatChecked) {
        return AgentGrindNavigationTargetSelector.selectGrindNavigationTarget(
                entry, agentPosition, combatTargetPosition, crossRegionRetreatChecked, hooks());
    }

    public static Point selectCrossRegionRetreatTarget(BotEntry entry,
                                                       Point agentPosition,
                                                       Point combatTargetPosition) {
        return AgentGrindNavigationTargetSelector.selectCrossRegionRetreatTarget(
                entry, agentPosition, combatTargetPosition, hooks());
    }

    public static boolean shouldUseLocalCombatRetreatTarget(BotEntry entry,
                                                            Point agentPosition,
                                                            Point combatTargetPosition,
                                                            Point retreatPosition) {
        return AgentGrindNavigationTargetSelector.shouldUseLocalCombatRetreatTarget(
                entry, agentPosition, combatTargetPosition, retreatPosition, hooks());
    }

    private static AgentGrindNavigationTargetSelector.NavigationHooks hooks() {
        return new AgentGrindNavigationTargetSelector.NavigationHooks(
                BotNavigationManager::resolveCurrentRegionId,
                BotNavigationManager::resolveTargetRegionId,
                BotNavigationManager::findPath,
                BotMovementManager.configuredGrindEdgeMargin(),
                BotMovementManager.configuredJumpYThreshold());
    }
}
