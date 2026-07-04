package server.bots;

import server.agents.capabilities.navigation.AgentNavigationGraphService;

import server.agents.capabilities.navigation.AgentNavigationGraph;

import server.agents.capabilities.movement.AgentClimbMovementService;
import server.agents.capabilities.movement.AgentAirborneMovementService;
import server.agents.capabilities.movement.AgentClimbMovementPolicy;
import server.agents.capabilities.movement.AgentFootholdIndexService;
import server.agents.capabilities.movement.AgentGroundMovementPolicy;
import server.agents.capabilities.movement.AgentGroundMovementRuntimeService;
import server.agents.capabilities.movement.AgentGroundMovementService;
import server.agents.capabilities.movement.AgentGroundPhysicsService;
import server.agents.capabilities.movement.AgentGroundTargetService;
import server.agents.capabilities.movement.AgentJumpActionService;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.movement.AgentMovementProfileService;
import server.agents.capabilities.movement.AgentMovementRecoveryService;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.capabilities.movement.AgentRopeMovementService;
import server.agents.capabilities.movement.AgentSwimMovementService;
import server.agents.capabilities.movement.fidget.AgentFidgetService;

import client.Character;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotGrindSearchStateRuntime;
import server.agents.integration.AgentBotGrindTargetStateRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMovementBroadcastStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotOwnerMotionStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.maps.Foothold;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.*;
import java.util.Map;

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

    static int velocityFromDeltaX(double deltaX) {
        return AgentGroundPhysicsService.velocityFromDeltaX(deltaX);
    }

    static void stopGroundMotion(BotEntry entry) {
        AgentGroundPhysicsService.stopGroundMotion(entry);
    }

    public static boolean refreshMovementProfile(BotEntry entry) {
        return AgentMovementProfileService.refreshMovementProfile(entry);
    }

    public static void resetEntryState(BotEntry entry) {
        AgentMovementStateResetService.resetEntryState(entry);
    }

    public static void resetEntryStateAfterTeleport(BotEntry entry) {
        AgentMovementStateResetService.resetEntryStateAfterTeleport(entry);
    }

    public static void clearNavigationState(BotEntry entry) {
        AgentMovementStateResetService.clearNavigationState(entry);
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

    static boolean shouldHoldClimbIdle(BotEntry entry, int dy, int dxOwner) {
        return AgentClimbMovementPolicy.shouldHoldClimbIdle(
                AgentBotNavigationDebugStateRuntime.hasActiveNavigationEdge(entry),
                AgentBotModeStateRuntime.grinding(entry),
                dy,
                dxOwner,
                cfg.STOP_DIST,
                cfg.FOLLOW_DIST);
    }

    static boolean shouldSnapToClimbTarget(BotEntry entry, Point targetPos, int dy) {
        if (entry == null) {
            return false;
        }
        // Allow target == bottomY: rope-exit launch anchors can be authored at the rope bottom
        // (pathlog-Leroy/John). The exclusive guard rejected those anchors, leaving the bot
        // grinding the climb integrator against a fixed-step overshoot - every step landed
        // past bottomY, beginFall(0,0) detached, repeat. Top step-off keeps its strict guard
        // because dismount there is driven by physics top-boundary detach, not snap.
        return AgentClimbMovementPolicy.shouldSnapToClimbTarget(
                AgentBotClimbStateRuntime.climbing(entry),
                AgentBotClimbStateRuntime.climbRope(entry),
                targetPos,
                dy,
                AgentBotNavigationDebugStateRuntime.navPreciseTarget(entry),
                AgentMovementKinematicsService.climbStepPerTick());
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

    static int preciseNavStopDist(AgentNavigationGraph.Edge navEdge) {
        return AgentGroundMovementPolicy.preciseNavStopDist(navEdge);
    }

    static Point adjustGrindingTargetPosition(BotEntry entry, Foothold currentFh, Point targetPos) {
        return AgentGroundTargetService.adjustGrindingTargetPosition(entry, currentFh, targetPos);
    }

    public static int resolveGroundStepX(BotEntry entry, Point botPos, Point targetPos, int stopDist, int followDist) {
        return AgentGroundMovementService.resolveGroundStepX(entry, botPos, targetPos, stopDist, followDist);
    }

    static int calcStepX(MapleMap map, int botX, int targetX, boolean wasMovingX) {
        return AgentGroundMovementService.calcStepX(map, botX, targetX, wasMovingX);
    }

    static int calcStepX(MapleMap map, int botX, int targetX, boolean wasMovingX, int stopDist, int followDist) {
        return AgentGroundMovementService.calcStepX(map, botX, targetX, wasMovingX, stopDist, followDist);
    }

    static int calcStepX(MapleMap map, AgentMovementProfile profile, int botX, int targetX, boolean wasMovingX, int stopDist, int followDist) {
        return AgentGroundMovementService.calcStepX(map, profile, botX, targetX, wasMovingX, stopDist, followDist);
    }

    static int updateStepX(BotEntry entry, MapleMap map, int botX, int targetX) {
        return AgentGroundMovementService.updateStepX(entry, map, botX, targetX);
    }

    static int updateStepX(BotEntry entry, MapleMap map, int botX, int targetX, int stopDist, int followDist) {
        return AgentGroundMovementService.updateStepX(entry, map, botX, targetX, stopDist, followDist);
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

    public static Map<Integer, Foothold> buildFhIndex(MapleMap map) {
        return AgentFootholdIndexService.buildFhIndex(map);
    }

}
