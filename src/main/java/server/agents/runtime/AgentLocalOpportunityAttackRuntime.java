package server.agents.runtime;

import client.Character;
import server.agents.capabilities.combat.AgentLocalOpportunityAttackService;
import server.agents.capabilities.movement.AgentJumpActionService;
import server.agents.capabilities.movement.AgentMovementKinematicsService;

import java.awt.Point;

public final class AgentLocalOpportunityAttackRuntime {
    private AgentLocalOpportunityAttackRuntime() {
    }

    public static AgentLocalOpportunityAttackService.Result tryLocalOpportunityAttack(AgentRuntimeEntry entry,
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

    public static AgentLiveModeTickRuntime.LocalAttackResult tryLocalOpportunityAttackForLiveMode(
            AgentRuntimeEntry entry,
            Character agent,
            Point agentPosition,
            Point movementTargetPosition,
            Point moveWindowReferencePosition,
            boolean allowCombatMovement,
            boolean allowJumpTowardTarget) {
        AgentLocalOpportunityAttackService.Result result = tryLocalOpportunityAttack(
                entry,
                agent,
                agentPosition,
                movementTargetPosition,
                moveWindowReferencePosition,
                allowCombatMovement,
                allowJumpTowardTarget);
        return new AgentLiveModeTickRuntime.LocalAttackResult(result.consumedTick(), result.targetPos());
    }

    private static AgentLocalOpportunityAttackService.Hooks hooks() {
        return new AgentLocalOpportunityAttackService.Hooks(
                AgentGrindNavigationRuntime::selectGrindNavigationTarget,
                AgentMovementKinematicsService::calculateMaxJumpHeight,
                AgentJumpActionService::initiateJump,
                AgentLocalOpportunityAttackRuntime::setLocalAttackMoveWindow);
    }

    private static void setLocalAttackMoveWindow(AgentRuntimeEntry entry, Point agentPosition, Point referencePosition) {
        AgentLocalAttackMoveWindowRuntime.setLocalAttackMoveWindow(entry, agentPosition, referencePosition);
    }
}
