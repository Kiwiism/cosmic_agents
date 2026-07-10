package server.agents.capabilities.recovery;

import client.Character;
import server.agents.capabilities.movement.AgentGroundingService;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Assembles movement operations used by distance-based Agent recovery.
 */
public final class AgentRecoveryTeleportCoordinator {
    private AgentRecoveryTeleportCoordinator() {
    }

    public static boolean recoverTeleportDistance(AgentRuntimeEntry entry,
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

    public static boolean recoverGrindPartyTeleportDistance(AgentRuntimeEntry entry,
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
                AgentGroundingService::findGroundPoint,
                AgentMovementPoseService::teleportTo,
                AgentMovementStateResetService::resetEntryStateAfterTeleport,
                AgentMovementBroadcastService::broadcastMovement);
    }
}
