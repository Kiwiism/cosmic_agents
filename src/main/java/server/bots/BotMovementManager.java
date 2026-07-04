package server.bots;

import server.agents.capabilities.movement.AgentClimbMovementService;
import server.agents.capabilities.movement.AgentAirborneMovementService;
import server.agents.capabilities.movement.AgentClimbMovementPolicy;
import server.agents.capabilities.movement.AgentGroundMovementRuntimeService;
import server.agents.capabilities.movement.AgentJumpActionService;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.movement.AgentMovementRecoveryService;
import server.agents.capabilities.movement.AgentRopeMovementService;
import server.agents.capabilities.movement.AgentSwimMovementService;
import server.agents.capabilities.movement.fidget.AgentFidgetService;

import client.Character;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.*;

public class BotMovementManager {

    static class Config extends BotPhysicsEngine.Config {
        public int STOP_DIST = 30;
        public int FOLLOW_DIST = 80;
        public int GRIND_EDGE_MARGIN = 40; // keep bot this many px from foothold edge while grinding
        public int MOB_AVOID_LOOKAHEAD_STEPS = 3;

        public int JUMP_Y_THRESH = 30;
        public int TELEPORT_DIST = 4000;
        // Tighter teleport trigger when the bot has slipped outside the map's VR rectangle.
        // Long falls below VRBottom never collide with anything and otherwise wait until the
        // 4000 Manhattan threshold; this lets us recover sooner once we know the bot is OOB.
        public int OOB_TELEPORT_DIST = 600;
        public int FOLLOW_Y_CAP = 200; // max vertical distance for Y-snapped follow target
    }

    static Config cfg = bindConfig(new Config());

    private static Config bindConfig(Config config) {
        BotPhysicsEngine.cfg = config;
        return config;
    }

    public static void tickClimbing(BotEntry entry, Point targetPos, boolean runAiTick) {
        AgentClimbMovementService.tickClimbing(entry, targetPos, runAiTick);
    }

    static void jumpOffRope(BotEntry entry, Character bot, int dx) {
        int airVelX = resolveAirVelocityX(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry), dx);
        AgentRopeMovementService.beginJumpOffRope(entry, bot, airVelX);
        broadcastMovement(entry);
    }

    static void jumpToRope(BotEntry entry, Character bot, int dx) {
        Rope sourceRope = AgentBotClimbStateRuntime.climbRope(entry);
        int airVelX = resolveAirVelocityX(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry), dx);
        AgentRopeMovementService.beginRopeTransferJump(entry, bot, sourceRope, airVelX);
        broadcastMovement(entry);
    }

    static boolean sameRope(Rope left, Rope right) {
        return AgentClimbMovementPolicy.sameRope(left, right);
    }

    public static void tickAirborne(BotEntry entry, Point targetPos) {
        AgentAirborneMovementService.tickAirborne(entry, targetPos);
    }

    public static void tickSwimming(BotEntry entry, Point targetPos) {
        AgentSwimMovementService.tickSwimming(entry, targetPos);
    }

    public static void tickGrounded(BotEntry entry, Point targetPos) {
        AgentGroundMovementRuntimeService.tickGrounded(entry, targetPos);
    }

    public static void initiateJump(BotEntry entry, Character bot, int dx) {
        AgentJumpActionService.initiateJump(entry, bot, dx);
    }

    public static void tickUnstuck(BotEntry entry) {
        AgentMovementRecoveryService.tickUnstuck(entry);
    }

    public static void initiateRopeJump(BotEntry entry, Character bot, int dx) {
        AgentJumpActionService.initiateRopeJump(entry, bot, dx);
    }

    private static int resolveAirVelocityX(MapleMap map, AgentMovementProfile profile, int dx) {
        return AgentJumpActionService.resolveAirVelocityX(map, profile, dx);
    }

    public static void broadcastMovement(BotEntry entry) {
        AgentMovementBroadcastService.broadcastMovement(entry);
    }

}
