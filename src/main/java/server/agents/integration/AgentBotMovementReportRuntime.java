package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentMovementDialogueReporter;
import server.bots.BotMovementManager;
import server.bots.BotMovementProfile;
import server.bots.BotPhysicsEngine;
import server.maps.FieldLimit;
import server.maps.MapleMap;

import java.util.List;

/**
 * Temporary Agent-owned movement-report data adapter while movement and physics
 * source data still comes from the bot runtime.
 */
public final class AgentBotMovementReportRuntime {
    private AgentBotMovementReportRuntime() {
    }

    public static List<String> movementStatsReport(Character bot) {
        if (bot == null) {
            return AgentMovementDialogueReporter.movementStatsReport(null, 0, 0, false, 0, null);
        }

        BotMovementProfile profile = BotMovementProfile.fromCharacter(bot);
        MapleMap map = bot.getMap();
        int rawSpeedStat = bot.getTotalMoveSpeedStat();
        int rawJumpStat = bot.getTotalJumpStat();
        boolean movementSkillsForced = map != null && FieldLimit.MOVEMENTSKILLS.check(map.getFieldLimit());
        AgentMovementDialogueReporter.MovementProfile agentProfile =
                new AgentMovementDialogueReporter.MovementProfile(
                        profile.totalSpeedStat(),
                        profile.totalJumpStat(),
                        profile.walkVelocityPxs(),
                        profile.hForcePxs(),
                        BotPhysicsEngine.jumpForcePerTick(profile),
                        BotPhysicsEngine.ropeJumpForcePerTick(profile),
                        BotPhysicsEngine.calculateMaxJumpHeight(profile));
        AgentMovementDialogueReporter.MapMovementProfile mapProfile = map == null
                ? null
                : new AgentMovementDialogueReporter.MapMovementProfile(
                        BotMovementManager.walkStep(map, profile),
                        BotPhysicsEngine.climbStepPerTick(),
                        BotPhysicsEngine.maxJumpHorizontalTravel(map, profile),
                        BotPhysicsEngine.maxRopeJumpHorizontalTravel(map, profile));
        return AgentMovementDialogueReporter.movementStatsReport(
                agentProfile,
                rawSpeedStat,
                rawJumpStat,
                movementSkillsForced,
                BotPhysicsEngine.climbStepPerTick(),
                mapProfile);
    }
}
