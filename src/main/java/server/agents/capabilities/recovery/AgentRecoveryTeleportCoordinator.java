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
        return recoverTeleportDistance(entry, agent, targetPosition, teleportDistance,
                outOfBoundsTeleportDistance, System.currentTimeMillis(),
                AgentNavigationRecoveryPolicy.mayPerformSoftTeleport());
    }

    static boolean recoverTeleportDistance(AgentRuntimeEntry entry,
                                           Character agent,
                                           Point targetPosition,
                                           int teleportDistance,
                                           int outOfBoundsTeleportDistance,
                                           long nowMs) {
        return recoverTeleportDistance(entry, agent, targetPosition, teleportDistance,
                outOfBoundsTeleportDistance, nowMs,
                AgentNavigationRecoveryPolicy.mayPerformSoftTeleport());
    }

    static boolean recoverTeleportDistance(AgentRuntimeEntry entry,
                                           Character agent,
                                           Point targetPosition,
                                           int teleportDistance,
                                           int outOfBoundsTeleportDistance,
                                           long nowMs,
                                           boolean softTeleportEnabled) {
        if (!AgentRecoveryTeleportService.isOutsideKnownMapBounds(agent)
                && (!softTeleportEnabled
                || !AgentNavigationRecoveryRuntime.tryAcquire(entry, "distance-teleport", nowMs)
                || !AgentNavigationRecoveryRuntime.teleportEscalationReady(entry, nowMs))) {
            return false;
        }
        boolean recovered = AgentRecoveryTeleportService.recoverTeleportDistance(
                entry,
                agent,
                targetPosition,
                teleportDistance,
                outOfBoundsTeleportDistance,
                hooks());
        if (recovered) {
            AgentNavigationRecoveryRuntime.recordProgress(entry);
        }
        return recovered;
    }

    public static boolean recoverGrindPartyTeleportDistance(AgentRuntimeEntry entry,
                                                            Character agent,
                                                            Character partyAnchor,
                                                            int teleportDistance,
                                                            int outOfBoundsTeleportDistance,
                                                            int multiplier) {
        long nowMs = System.currentTimeMillis();
        if (!AgentRecoveryTeleportService.isOutsideKnownMapBounds(agent)
                && (!AgentNavigationRecoveryPolicy.mayPerformSoftTeleport()
                || !AgentNavigationRecoveryRuntime.tryAcquire(entry, "party-teleport", nowMs)
                || !AgentNavigationRecoveryRuntime.teleportEscalationReady(entry, nowMs))) {
            return false;
        }
        boolean recovered = AgentRecoveryTeleportService.recoverGrindPartyTeleportDistance(
                entry,
                agent,
                partyAnchor,
                teleportDistance,
                outOfBoundsTeleportDistance,
                multiplier,
                hooks());
        if (recovered) {
            AgentNavigationRecoveryRuntime.recordProgress(entry);
        }
        return recovered;
    }

    private static AgentRecoveryTeleportService.RecoveryHooks hooks() {
        return new AgentRecoveryTeleportService.RecoveryHooks(
                AgentGroundingService::findGroundPoint,
                AgentMovementPoseService::teleportTo,
                AgentMovementStateResetService::resetEntryStateAfterTeleport,
                AgentMovementBroadcastService::broadcastMovement);
    }
}
