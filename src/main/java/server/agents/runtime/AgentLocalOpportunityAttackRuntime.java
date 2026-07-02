package server.agents.runtime;

import client.Character;
import server.agents.capabilities.combat.AgentLocalOpportunityAttackService;
import server.bots.BotEntry;
import server.bots.BotManager;
import server.bots.BotMovementManager;
import server.bots.BotPhysicsEngine;

import java.awt.Point;

public final class AgentLocalOpportunityAttackRuntime {
    private AgentLocalOpportunityAttackRuntime() {
    }

    public static AgentLocalOpportunityAttackService.Result tryLocalOpportunityAttack(BotEntry entry,
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
                BotManager::selectGrindNavigationTarget,
                BotPhysicsEngine::calculateMaxJumpHeight,
                BotMovementManager::initiateJump,
                AgentLocalOpportunityAttackRuntime::setLocalAttackMoveWindow);
    }

    private static void setLocalAttackMoveWindow(BotEntry entry, Point agentPosition, Point referencePosition) {
        AgentLocalAttackMoveWindowService.setLocalAttackMoveWindow(
                entry,
                agentPosition,
                referencePosition,
                BotMovementManager.configuredFollowDist(),
                BotMovementManager.configuredStopDist(),
                BotMovementManager.configuredFollowYCap());
    }
}
