package server.agents.capabilities.combat;

import client.Character;
import server.agents.capabilities.movement.AgentJumpActionService;
import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Coordinates combat target selection, jump initiation, and movement-window
 * state for a local opportunity attack.
 */
public final class AgentLocalOpportunityAttackCoordinator {
    private AgentLocalOpportunityAttackCoordinator() {
    }

    public static AgentLocalOpportunityAttackService.Result tryLocalOpportunityAttack(
            AgentRuntimeEntry entry,
            Character agent,
            Point agentPosition,
            Point movementTargetPosition,
            Point moveWindowReferencePosition,
            boolean allowCombatMovement,
            boolean allowJumpTowardTarget) {
        return AgentLocalOpportunityAttackService.tryLocalOpportunityAttack(
                entry,
                agent,
                agentPosition,
                movementTargetPosition,
                moveWindowReferencePosition,
                allowCombatMovement,
                allowJumpTowardTarget,
                hooks());
    }

    private static AgentLocalOpportunityAttackService.Hooks hooks() {
        return new AgentLocalOpportunityAttackService.Hooks(
                AgentGrindNavigationTargetSelector::selectGrindNavigationTarget,
                AgentMovementKinematicsService::calculateMaxJumpHeight,
                AgentJumpActionService::initiateJump,
                AgentLocalOpportunityAttackCoordinator::setLocalAttackMoveWindow);
    }

    private static void setLocalAttackMoveWindow(AgentRuntimeEntry entry,
                                                 Point agentPosition,
                                                 Point referencePosition) {
        AgentLocalAttackMoveWindowService.setLocalAttackMoveWindow(entry, agentPosition, referencePosition);
    }
}
