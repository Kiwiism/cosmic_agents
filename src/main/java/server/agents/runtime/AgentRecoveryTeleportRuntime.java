package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementBroadcastService;

import client.Character;
import server.bots.BotEntry;
import server.bots.BotMovementManager;
import server.bots.BotPhysicsEngine;

import java.awt.Point;

public final class AgentRecoveryTeleportRuntime {
    private AgentRecoveryTeleportRuntime() {
    }

    public static boolean recoverTeleportDistance(BotEntry entry,
                                                  Character agent,
                                                  Point targetPosition,
                                                  int teleportDistance,
                                                  int outOfBoundsTeleportDistance) {
        return AgentRecoveryTeleportService.recoverTeleportDistance(
                entry,
                agent,
                targetPosition,
                teleportDistance,
                outOfBoundsTeleportDistance,
                hooks());
    }

    public static boolean recoverGrindPartyTeleportDistance(BotEntry entry,
                                                            Character agent,
                                                            Character partyAnchor,
                                                            int teleportDistance,
                                                            int outOfBoundsTeleportDistance,
                                                            int multiplier) {
        return AgentRecoveryTeleportService.recoverGrindPartyTeleportDistance(
                entry,
                agent,
                partyAnchor,
                teleportDistance,
                outOfBoundsTeleportDistance,
                multiplier,
                hooks());
    }

    private static AgentRecoveryTeleportService.RecoveryHooks hooks() {
        return new AgentRecoveryTeleportService.RecoveryHooks(
                BotPhysicsEngine::findGroundPoint,
                BotPhysicsEngine::teleportTo,
                BotMovementManager::resetEntryStateAfterTeleport,
                AgentMovementBroadcastService::broadcastMovement);
    }
}
