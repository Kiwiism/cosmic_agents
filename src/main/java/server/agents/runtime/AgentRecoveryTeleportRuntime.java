package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementStateResetService;

import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentGroundingService;
import server.agents.capabilities.movement.AgentMovementPoseService;

import client.Character;
import server.bots.BotEntry;

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
                AgentGroundingService::findGroundPoint,
                (entry, agent, position) -> AgentMovementPoseService.teleportTo(asBotEntry(entry), agent, position),
                entry -> AgentMovementStateResetService.resetEntryStateAfterTeleport(asBotEntry(entry)),
                entry -> AgentMovementBroadcastService.broadcastMovement(asBotEntry(entry)));
    }

    private static BotEntry asBotEntry(AgentRuntimeEntry entry) {
        return (BotEntry) entry;
    }
}
