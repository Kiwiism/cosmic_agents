package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentMovementDialogueReporter;
import server.agents.capabilities.movement.AgentMovementKinematicsSnapshot;

import java.util.List;

/**
 * Temporary Agent-owned movement-report data adapter while movement and physics
 * source data still comes from the bot runtime.
 */
public final class AgentBotMovementReportRuntime {
    private AgentBotMovementReportRuntime() {
    }

    public static List<String> movementStatsReport(Character bot) {
        AgentMovementKinematicsSnapshot snapshot = AgentBotMovementKinematicsRuntime.snapshot(bot);
        if (snapshot == null) {
            return AgentMovementDialogueReporter.movementStatsReport(null, 0, 0, false, 0, null);
        }

        AgentMovementKinematicsSnapshot.MovementProfile profile = snapshot.movementProfile();
        AgentMovementDialogueReporter.MovementProfile agentProfile =
                new AgentMovementDialogueReporter.MovementProfile(
                        profile.totalSpeedStat(),
                        profile.totalJumpStat(),
                        profile.walkVelocityPxs(),
                        profile.hForcePxs(),
                        profile.jumpForcePerTick(),
                        profile.ropeJumpForcePerTick(),
                        profile.maxJumpHeight());
        AgentMovementKinematicsSnapshot.MapMovementProfile mapProfile = snapshot.mapMovementProfile();
        AgentMovementDialogueReporter.MapMovementProfile agentMapProfile = mapProfile == null
                ? null
                : new AgentMovementDialogueReporter.MapMovementProfile(
                        mapProfile.walkStep(),
                        mapProfile.climbStepPerTick(),
                        mapProfile.maxJumpHorizontalTravel(),
                        mapProfile.maxRopeJumpHorizontalTravel());
        return AgentMovementDialogueReporter.movementStatsReport(
                agentProfile,
                snapshot.rawSpeedStat(),
                snapshot.rawJumpStat(),
                snapshot.movementSkillsForced(),
                snapshot.climbStepPerTick(),
                agentMapProfile);
    }
}
