package server.bots;

import server.agents.capabilities.navigation.AgentNavigationGraphService;

import server.agents.capabilities.navigation.AgentNavigationGraph;

import server.agents.runtime.AgentPerformanceMonitor;

import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.movement.AgentClimbMovementService;
import server.agents.capabilities.movement.AgentAirborneMovementService;
import server.agents.capabilities.movement.AgentClimbMovementPolicy;
import server.agents.capabilities.movement.AgentFallbackMovementService;
import server.agents.capabilities.movement.AgentFootholdIndexService;
import server.agents.capabilities.movement.AgentGroundMovementPolicy;
import server.agents.capabilities.movement.AgentGroundMovementService;
import server.agents.capabilities.movement.AgentGroundTargetService;
import server.agents.capabilities.movement.AgentJumpActionService;
import server.agents.capabilities.movement.AgentJumpProbeService;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.movement.AgentMovementProfileService;
import server.agents.capabilities.movement.AgentMovementRecoveryService;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.capabilities.movement.AgentMovementTimers;
import server.agents.capabilities.movement.AgentSwimMovementService;
import server.agents.capabilities.movement.fidget.AgentFidgetService;

import client.Character;
import server.agents.integration.AgentBotCombatCooldownStateRuntime;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotGrindSearchStateRuntime;
import server.agents.integration.AgentBotGrindTargetStateRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMovementBroadcastStateRuntime;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotOwnerMotionStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.integration.AgentBotSwimStateRuntime;
import server.agents.capabilities.combat.data.AgentMobHitboxProvider;
import server.life.Monster;
import server.maps.Foothold;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.*;
import java.util.Map;

public class BotMovementManager {
    enum ActionType {
        IDLE,
        WALK,
        CROUCH,
        JUMP,
        CLIMB_UP,
        CLIMB_DOWN
    }

    record MoveAction(ActionType type, int stepX) {
        private static final MoveAction IDLE = new MoveAction(ActionType.IDLE, 0);
        private static final MoveAction CROUCH = new MoveAction(ActionType.CROUCH, 0);
        private static final MoveAction CLIMB_UP = new MoveAction(ActionType.CLIMB_UP, 0);
        private static final MoveAction CLIMB_DOWN = new MoveAction(ActionType.CLIMB_DOWN, 0);

        static MoveAction idle() {
            return IDLE;
        }

        static MoveAction walk(int stepX) {
            return new MoveAction(ActionType.WALK, stepX);
        }

        static MoveAction crouch() {
            return CROUCH;
        }

        static MoveAction jump(int stepX) {
            return new MoveAction(ActionType.JUMP, stepX);
        }

        static MoveAction climbUp() {
            return CLIMB_UP;
        }

        static MoveAction climbDown() {
            return CLIMB_DOWN;
        }
    }

    public static final class JumpLanding {
        private final Point point;
        private final Foothold foothold;

        JumpLanding(Point point, Foothold foothold) {
            this.point = point;
            this.foothold = foothold;
        }

        public Point point() {
            return point;
        }

        public Foothold foothold() {
            return foothold;
        }
    }

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

    public static int configuredWalkVelocityPxs() {
        return AgentMovementPhysicsConfig.configuredWalkVelocityPxs();
    }

    public static int configuredTickMs() {
        return AgentMovementPhysicsConfig.configuredMovementTickMs();
    }

    public static int configuredFollowDist() {
        return AgentMovementPhysicsConfig.configuredFollowDist();
    }

    public static int configuredStopDist() {
        return AgentMovementPhysicsConfig.configuredStopDist();
    }

    public static int configuredTeleportDist() {
        return AgentMovementPhysicsConfig.configuredTeleportDist();
    }

    public static int configuredOutOfBoundsTeleportDist() {
        return AgentMovementPhysicsConfig.configuredOutOfBoundsTeleportDist();
    }

    public static int configuredFollowYCap() {
        return AgentMovementPhysicsConfig.configuredFollowYCap();
    }

    public static int configuredJumpYThreshold() {
        return AgentMovementPhysicsConfig.configuredJumpYThreshold();
    }

    public static int configuredGrindEdgeMargin() {
        return AgentMovementPhysicsConfig.configuredGrindEdgeMargin();
    }

    private static Config bindConfig(Config config) {
        BotPhysicsEngine.cfg = config;
        return config;
    }

    public static int tickDown(int remainingMs) {
        return AgentMovementTimers.tickDown(remainingMs);
    }

    public static int delayAfterCurrentTick(int durationMs) {
        return AgentMovementTimers.delayAfterCurrentTick(durationMs);
    }

    static int walkStep(MapleMap map) {
        return AgentMovementKinematicsService.walkStep(map);
    }

    public static int walkStep(MapleMap map, AgentMovementProfile profile) {
        return AgentMovementKinematicsService.walkStep(map, profile);
    }

    static int velocityFromDeltaX(double deltaX) {
        return BotPhysicsEngine.velocityFromDeltaX(deltaX);
    }

    static void stopGroundMotion(BotEntry entry) {
        BotPhysicsEngine.stopGroundMotion(entry);
    }

    static JumpLanding simulateJumpLanding(MapleMap map, Point from, int stepX) {
        return wrapLanding(AgentJumpProbeService.simulateJumpLanding(map, from, stepX));
    }

    public static JumpLanding simulateJumpLanding(MapleMap map, Point from, int stepX, AgentMovementProfile profile) {
        return wrapLanding(AgentJumpProbeService.simulateJumpLanding(map, from, stepX, profile));
    }

    static JumpLanding simulateRopeJumpLanding(MapleMap map, Point from, int stepX) {
        return wrapLanding(AgentJumpProbeService.simulateRopeJumpLanding(map, from, stepX, AgentMovementProfile.base()));
    }

    static JumpLanding simulateRopeJumpLanding(MapleMap map, Point from, int stepX, AgentMovementProfile profile) {
        return wrapLanding(AgentJumpProbeService.simulateRopeJumpLanding(map, from, stepX, profile));
    }

    static boolean canReachRopeFromGround(MapleMap map, Point from, Rope rope) {
        return AgentJumpProbeService.canReachRopeFromGround(map, from, rope);
    }

    public static boolean canReachRopeFromGround(MapleMap map, Point from, Rope rope, AgentMovementProfile profile) {
        return AgentJumpProbeService.canReachRopeFromGround(map, from, rope, profile);
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
        if (useAgentClimbMovement()) {
            AgentClimbMovementService.tickClimbing(entry, targetPos, runAiTick);
            return;
        }
        long startedAt = System.nanoTime();
        try {
            Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
            // Null rope is handled inside advanceClimb/holdClimb — they call beginFall internally.
            BotPhysicsEngine.tickMotionTimers(entry);
            Point botPos = bot.getPosition();
            int dy = targetPos.y - botPos.y;
            Rope climbRope = AgentBotClimbStateRuntime.climbRope(entry);
            int dxOwner = targetPos.x - climbRope.x();

            // If not navigating, allow jumping off when target is far away horizontally
            if (runAiTick && !AgentBotNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)
                    && Math.abs(dxOwner) > cfg.FOLLOW_DIST
                    && climbRope.bottomY() < targetPos.y) {
                jumpOffRope(entry, bot, dxOwner);
                return;
            }

            boolean climbIdle = shouldHoldClimbIdle(entry, dy, dxOwner);
            if (climbIdle) {
                BotPhysicsEngine.holdClimb(entry, bot);
                broadcastMovement(entry);
                return;
            }

            if (shouldSnapToClimbTarget(entry, targetPos, dy)) {
                BotPhysicsEngine.attachToRope(entry, bot, climbRope, targetPos.y);
                broadcastMovement(entry);
                return;
            }

            if (!runAiTick && !AgentBotNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)) {
                // No committed nav edge → no AI-decided climb intent. On non-AI ticks the
                // navDirective falls through to the raw follow target (resolveTarget can't run
                // findNextEdge here), and using its dy to choose a direction can dismount the bot
                // off the rope-top onto the foothold above — pathlog-Preston-2026-05-07 oscillation.
                // Integrate the cached intent instead; the next AI tick will refresh direction.
                if (!AgentBotClimbStateRuntime.hasClimbVerticalDirection(entry)) {
                    BotPhysicsEngine.holdClimb(entry, bot);
                } else {
                    BotPhysicsEngine.advanceClimb(entry, bot);
                }
                broadcastMovement(entry);
                return;
            }

            // Committed climb edges must reach the exact launch anchor so execution can hand off.
            MoveAction action = dy < 0
                    ? MoveAction.climbUp()
                    : dy > 0 ? MoveAction.climbDown() : MoveAction.idle();
            applyClimbAction(entry, bot, action);
        } finally {
            AgentPerformanceMonitor.record("move-climb", System.nanoTime() - startedAt);
        }
    }

    static void jumpOffRope(BotEntry entry, Character bot, int dx) {
        int airVelX = resolveAirVelocityX(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry), dx);
        BotPhysicsEngine.beginJumpOffRope(entry, bot, airVelX);
        broadcastMovement(entry);
    }

    static void jumpToRope(BotEntry entry, Character bot, int dx) {
        Rope sourceRope = AgentBotClimbStateRuntime.climbRope(entry);
        int airVelX = resolveAirVelocityX(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry), dx);
        BotPhysicsEngine.beginRopeTransferJump(entry, bot, sourceRope, airVelX);
        broadcastMovement(entry);
    }

    private static void applyClimbAction(BotEntry entry, Character bot, MoveAction action) {
        AgentBotClimbStateRuntime.setClimbVerticalDirection(entry, switch (action.type()) {
            case CLIMB_UP -> -1;
            case CLIMB_DOWN -> 1;
            default -> 0;
        });

        if (!AgentBotClimbStateRuntime.hasClimbVerticalDirection(entry)) {
            BotPhysicsEngine.holdClimb(entry, bot);
        } else {
            BotPhysicsEngine.advanceClimb(entry, bot);
        }
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
                BotPhysicsEngine.climbStepPerTick());
    }

    public static void tickAirborne(BotEntry entry, Point targetPos) {
        if (useAgentAirborneMovement()) {
            AgentAirborneMovementService.tickAirborne(entry, targetPos);
            return;
        }
        long startedAt = System.nanoTime();
        try {
            AgentBotSwimStateRuntime.setSwimming(entry, false);
            BotPhysicsEngine.tickMotionTimers(entry);

            Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
            Point botPos = bot.getPosition();

            if (successfullyGrabbedRope(entry, bot, botPos)) {
                return;
            }

            // Set air steering intent. Gated by shouldApplyAirSteering to preserve
            // fixed ballistic path for committed nav jumps/drops.
            // If fidget manager already set moveDir (non-zero), preserve it.
            if (!AgentBotMovementStateRuntime.hasMoveDirection(entry) && targetPos != null && shouldApplyAirSteering(entry)) {
                int dx = targetPos.x - botPos.x;
                AgentBotMovementStateRuntime.setMoveDirection(entry,
                        Math.abs(dx) > BotPhysicsEngine.cfg.SWIM_ARRIVAL_RADIUS_PX
                                ? Integer.signum(dx) : 0);
            }

            BotPhysicsEngine.AirborneStepResult result = BotPhysicsEngine.stepAirborne(entry, bot);
            if (result == BotPhysicsEngine.AirborneStepResult.WALL) {
                if (successfullyGrabbedRope(entry, bot, bot.getPosition())) {
                    return;
                }
                broadcastMovement(entry);
                return;
            }
            if (result == BotPhysicsEngine.AirborneStepResult.CEILING) {
                broadcastMovement(entry);
                return;
            }
            if (result == BotPhysicsEngine.AirborneStepResult.LANDED) {
                AgentBotMovementPhysicsStateRuntime.clearJumpCooldown(entry);
                broadcastMovement(entry);
                return;
            }

            // CONTINUE — position advanced, check for rope grab at new position
            if (successfullyGrabbedRope(entry, bot, bot.getPosition())) {
                return;
            }
            broadcastMovement(entry);
        } finally {
            AgentPerformanceMonitor.record("move-air", System.nanoTime() - startedAt);
        }
    }

    private static boolean useAgentClimbMovement() {
        return true;
    }

    private static boolean useAgentAirborneMovement() {
        return true;
    }

    private static boolean successfullyGrabbedRope(BotEntry entry, Character bot, Point botPos) {
        if (!AgentBotClimbStateRuntime.climbUpIntent(entry)) {
            return false;
        }

        for (Rope rope : bot.getMap().getRopes()) {
            if (sameRope(AgentBotClimbStateRuntime.blockedRopeGrab(entry), rope)) {
                continue;
            }
            if (Math.abs(rope.x() - botPos.x) > BotPhysicsEngine.cfg.ROPE_GRAB_X) {
                continue;
            }
            if (botPos.y < rope.topY() || botPos.y > rope.bottomY() + 2) {
                continue;
            }

            BotPhysicsEngine.attachToRope(entry, bot, rope, botPos.y);
            broadcastMovement(entry);
            return true;
        }

        return false;
    }

    static boolean sameRope(Rope left, Rope right) {
        return AgentClimbMovementPolicy.sameRope(left, right);
    }

    private static boolean shouldApplyAirSteering(BotEntry entry) {
        if (AgentBotMovementPhysicsStateRuntime.fixedAirArc(entry)) {
            return false;
        }
        if (AgentBotMovementStateRuntime.hasDownJumpGracePeriod(entry)) {
            return false;
        }
        AgentNavigationGraph.Edge navEdge = (AgentNavigationGraph.Edge) AgentBotNavigationDebugStateRuntime.activeNavigationEdge(entry);
        if (navEdge == null) {
            return true;
        }
        return navEdge.type != AgentNavigationGraph.EdgeType.JUMP
                && navEdge.type != AgentNavigationGraph.EdgeType.DROP
                && !(navEdge.type == AgentNavigationGraph.EdgeType.CLIMB
                && navEdge.launchStepX != 0);
    }

    public static void tickSwimming(BotEntry entry, Point targetPos) {
        AgentSwimMovementService.tickSwimming(entry, targetPos);
    }

    /**
     * Translate a nav target into the discrete swim controls the real client exposes:
     * steer L/R (continuous), JUMP burst (one-shot), UP/DOWN held.
     * No continuous velocity steering — physics integrates the intents.
     */
    private static void computeSwimIntents(BotEntry entry, Point targetPos) {
        // Capture last vertical hold for hysteresis. Without sticky-middle,
        // a target sinking faster than the bot's UP-terminal sink rate causes
        // dy to oscillate across the LEVEL_BAND boundary every tick — bot
        // alternates UP-hold (slow sink) and free-sink, visibly stuttering.
        int prevVerticalHold = AgentBotSwimStateRuntime.swimVerticalHold(entry);

        // Default to "no input": bot drifts under swim gravity.
        AgentBotSwimStateRuntime.clearSwimInput(entry);

        // Player can't dispatch movement input (strafe/jump/up/down) while
        // CUserLocal::IsAttacking is true. Mirror that here: during animation
        // lock the integrator still ticks (drag + gravity, collision) but no
        // intent is set, so the bot just floats in place.
        if (AgentBotCombatCooldownStateRuntime.hasAttackCooldown(entry)) {
            return;
        }

        if (targetPos == null) {
            // Idle in water — hold UP so the bot doesn't sink endlessly.
            AgentBotSwimStateRuntime.setSwimVerticalHold(entry, -1);
            return;
        }

        Point pos = AgentBotRuntimeIdentityRuntime.bot(entry).getPosition();
        int dx = targetPos.x - pos.x;
        int dy = targetPos.y - pos.y;

        // Horizontal steer.
        int hRadius = BotPhysicsEngine.cfg.SWIM_ARRIVAL_RADIUS_PX;
        if (dx >  hRadius) AgentBotSwimStateRuntime.setSwimMoveDirection(entry, 1);
        else if (dx < -hRadius) AgentBotSwimStateRuntime.setSwimMoveDirection(entry, -1);

        // Arrival band: bot is essentially on top of the target both axes.
        // Hold UP just to maintain altitude, no burst, no horizontal push —
        // prevents the jump/sink oscillation when bot overshoots target by a
        // few px (was: any dy<0 fired a 1000+ px/s burst, then bot fell back
        // through level, repeat).
        int levelBand = BotPhysicsEngine.cfg.SWIM_LEVEL_BAND_PX;
        if (Math.abs(dx) <= hRadius && Math.abs(dy) <= levelBand) {
            AgentBotSwimStateRuntime.setSwimMoveDirection(entry, 0);
            AgentBotSwimStateRuntime.setSwimVerticalHold(entry, -1);
            return;
        }

        // Vertical intent with hysteresis around band boundaries. The middle
        // band (LEVEL < dy <= DOWN) is "sticky" — we keep whichever hold was
        // active last tick so the bot doesn't flip-flop between UP and free
        // sink as dy crosses LEVEL_BAND each frame while chasing a target
        // that sinks faster than UP-terminal.
        long now = System.currentTimeMillis();
        int jumpTrigger = BotPhysicsEngine.cfg.SWIM_JUMP_TRIGGER_DY_PX;
        int downBand = BotPhysicsEngine.cfg.SWIM_DOWN_BAND_PX;
        if (dy <= -jumpTrigger && now >= AgentBotSwimStateRuntime.swimNextJumpAtMs(entry)) {
            AgentBotSwimStateRuntime.setSwimJumpRequested(entry, true);
            AgentBotSwimStateRuntime.setSwimNextJumpAtMs(entry, now + BotPhysicsEngine.cfg.SWIM_JUMP_COOLDOWN_MS);
            AgentBotSwimStateRuntime.setSwimVerticalHold(entry, -1);
        } else if (dy <= levelBand) {
            AgentBotSwimStateRuntime.setSwimVerticalHold(entry, -1);        // clearly above target → UP
        } else if (dy > downBand) {
            AgentBotSwimStateRuntime.setSwimVerticalHold(entry, 1);         // clearly far below → DOWN
        } else {
            // Middle band: persist last hold to avoid stutter. If we were
            // sinking (free or DOWN), keep that — UP would just slow our
            // descent and let target pull further away. If we were UP-holding
            // and now drifted past LEVEL, switch to free sink so we catch up.
            AgentBotSwimStateRuntime.setSwimVerticalHold(entry, prevVerticalHold > 0 ? 1 : 0);
        }
    }

    public static void tickGrounded(BotEntry entry, Point targetPos) {
        long startedAt = System.nanoTime();
        try {
            AgentBotSwimStateRuntime.setSwimming(entry, false);
            Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);

            BotPhysicsEngine.tickMotionTimers(entry);

            Foothold currentFh = BotPhysicsEngine.syncAndDetectGround(entry, bot);
            if (currentFh == null) {
                broadcastMovement(entry);
                return;
            }

            Point botPos = bot.getPosition();
            if (AgentBotClimbStateRuntime.ropeEntryPending(entry)) {
                performTopRopeEntry(entry);
                return;
            }
            if (AgentBotMovementStateRuntime.hasDownJumpPending(entry)) {
                performDownJump(entry);
                return;
            }

            targetPos = adjustGrindingTargetPosition(entry, currentFh, targetPos);
            if (AgentBotNavigationDebugStateRuntime.graphWarmupFallback(entry) && targetPos != null) {
                if (AgentFallbackMovementService.tryImmediateAction(entry, botPos, targetPos)) {
                    return;
                }
                targetPos = AgentFallbackMovementService.resolveSteeringTarget(entry, botPos, targetPos);
            }
            MoveAction action = planGroundAction(entry, currentFh, botPos, targetPos);
            applyGroundAction(entry, currentFh, action);
        } finally {
            AgentPerformanceMonitor.record("move-ground", System.nanoTime() - startedAt);
        }
    }

    /**
     * Stop-distance used when navPreciseTarget is true.
     * WALK edges use 4px to absorb terrain micro-bumps on sloped footholds.
     * JUMP and straight down-jump DROP edges use 0px because the bot must walk INTO the
     * authored launch window, not stop just outside it. Other precise edge types
     * (CLIMB, PORTAL, non-windowed fallback cases) use 1px to reach the exact anchor.
     */
    static int preciseNavStopDist(AgentNavigationGraph.Edge navEdge) {
        if (navEdge != null
                && (navEdge.type == AgentNavigationGraph.EdgeType.JUMP
                || (navEdge.type == AgentNavigationGraph.EdgeType.DROP && navEdge.launchStepX == 0))) {
            // Bot must walk INTO the launch window, not just near it. The launch window checks
            // are strict, so stopDist=1 can halt the bot exactly 1px before the valid range.
            return 0;
        }
        if (navEdge != null && navEdge.type != AgentNavigationGraph.EdgeType.WALK) {
            return 1;
        }
        return 4;
    }

    static Point adjustGrindingTargetPosition(BotEntry entry, Foothold currentFh, Point targetPos) {
        return AgentGroundTargetService.adjustGrindingTargetPosition(entry, currentFh, targetPos);
    }

    private static MoveAction planGroundAction(BotEntry entry, Foothold currentFh, Point botPos, Point targetPos) {
        AgentNavigationGraph.Edge navEdge = (AgentNavigationGraph.Edge) AgentBotNavigationDebugStateRuntime.activeNavigationEdge(entry);
        boolean directionalDrop = isDirectionalDropEdge(navEdge);
        int stopDist = directionalDrop ? 0 : AgentBotNavigationDebugStateRuntime.navPreciseTarget(entry) ? preciseNavStopDist(navEdge) : cfg.STOP_DIST;
        // No hysteresis when navigating to an edge — always move toward the waypoint
        int followDist = directionalDrop ? 0
                : (navEdge != null || AgentBotNavigationDebugStateRuntime.navPreciseTarget(entry)) ? stopDist : cfg.FOLLOW_DIST;
        int stepX = resolveGroundStepX(entry, botPos, targetPos, stopDist, followDist);
        if (stepX == 0) {
            return MoveAction.idle();
        }
        boolean canWalkStep = BotPhysicsEngine.canWalkGroundStep(AgentBotRuntimeIdentityRuntime.botMap(entry), botPos, stepX);
        if (!canWalkStep) {
            boolean blockedByWall = BotPhysicsEngine.isGroundStepBlockedByWall(AgentBotRuntimeIdentityRuntime.botMap(entry), botPos, stepX);
            if (!blockedByWall
                    && ((directionalDrop && Integer.signum(stepX) == Integer.signum(navEdge.launchStepX))
                    || AgentFallbackMovementService.shouldWalkOffLedge(entry, botPos, targetPos, stepX))) {
                // Walk-off drops should keep walking in the authored direction until physics
                // detects lost ground and transitions into a fall with preserved momentum.
                return MoveAction.walk(stepX);
            }
            // Wall-blocked nav edges are stale or invalid. Clear them so the next AI tick can
            // replan instead of holding a walk stance into the wall.
            if (blockedByWall && navEdge != null) {
                clearNavigationState(entry);
            } else if (navEdge != null && navEdge.type == AgentNavigationGraph.EdgeType.WALK) {
                clearNavigationState(entry);
            }
            return MoveAction.idle();
        }
        if (shouldJumpToAvoidMob(entry, currentFh, botPos, stepX)) {
            return MoveAction.jump(stepX);
        }
        return MoveAction.walk(stepX);
    }

    private static boolean shouldJumpToAvoidMob(BotEntry entry, Foothold currentFh, Point botPos, int stepX) {
        if (entry == null || !AgentBotRuntimeIdentityRuntime.hasBot(entry) || currentFh == null || botPos == null || stepX == 0) {
            return false;
        }
        if ((!AgentBotModeStateRuntime.following(entry) && !AgentBotModeStateRuntime.grinding(entry))
                || AgentBotNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)
                || AgentBotNavigationDebugStateRuntime.navPreciseTarget(entry)) {
            return false;
        }

        Monster blockingMob = firstBlockingMobInWalkLane(entry, currentFh, botPos, stepX);
        if (blockingMob == null) {
            return false;
        }

        return simulatedJumpLandsInCurrentRegion(entry, currentFh, botPos, stepX);
    }

    private static Monster firstBlockingMobInWalkLane(BotEntry entry, Foothold currentFh, Point botPos, int stepX) {
        MapleMap map = AgentBotRuntimeIdentityRuntime.botMap(entry);
        int direction = Integer.signum(stepX);
        int lookahead = Math.max(Math.abs(stepX),
                BotPhysicsEngine.walkStep(map, AgentBotMovementStateRuntime.movementProfile(entry))
                        * Math.max(1, cfg.MOB_AVOID_LOOKAHEAD_STEPS));
        int laneEndX = botPos.x + direction * lookahead;
        Rectangle lane = inclusiveRectangle(
                Math.min(botPos.x, laneEndX),
                botPos.y - AgentCombatConfig.cfg.MOB_TOUCH_SWEEP_HEIGHT,
                Math.max(botPos.x, laneEndX),
                botPos.y);

        Monster nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (Monster mob : map.getAllMonsters()) {
            if (!mob.isAlive() || !isMobInCurrentGroundRegion(entry, currentFh, mob)) {
                continue;
            }

            Rectangle bounds = AgentMobHitboxProvider.getInstance().getMobBounds(mob);
            if (bounds == null) {
                bounds = inclusiveRectangle(mob.getPosition().x, mob.getPosition().y, mob.getPosition().x, mob.getPosition().y);
            }
            if (!lane.intersects(bounds) && !lane.contains(mob.getPosition())) {
                continue;
            }

            int mobEdgeX = direction > 0 ? bounds.x : bounds.x + bounds.width;
            int distance = Math.max(0, direction > 0 ? mobEdgeX - botPos.x : botPos.x - mobEdgeX);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = mob;
            }
        }
        return nearest;
    }

    private static boolean isMobInCurrentGroundRegion(BotEntry entry, Foothold currentFh, Monster mob) {
        Foothold mobFoothold = BotPhysicsEngine.findGroundFoothold(AgentBotRuntimeIdentityRuntime.botMap(entry), mob.getPosition());
        if (mobFoothold != null && mobFoothold.getId() == currentFh.getId()) {
            return true;
        }

        AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(
                AgentBotRuntimeIdentityRuntime.botMap(entry), AgentBotMovementStateRuntime.movementProfile(entry));
        if (graph == null) {
            return false;
        }

        int currentRegionId = BotNavigationManager.resolveCurrentRegionId(
                graph, entry, AgentBotRuntimeIdentityRuntime.botMap(entry), AgentBotRuntimeIdentityRuntime.bot(entry).getPosition());
        int mobRegionId = BotNavigationManager.resolveTargetRegionId(
                graph, entry, AgentBotRuntimeIdentityRuntime.botMap(entry), mob.getPosition());
        return currentRegionId >= 0 && currentRegionId == mobRegionId;
    }

    private static boolean simulatedJumpLandsInCurrentRegion(BotEntry entry, Foothold currentFh, Point botPos, int stepX) {
        MapleMap map = AgentBotRuntimeIdentityRuntime.botMap(entry);
        AgentMovementProfile profile = AgentBotMovementStateRuntime.movementProfile(entry);
        int airVelX = resolveAirVelocityX(map, profile, stepX);
        JumpLanding landing = simulateJumpLanding(map, botPos, airVelX, profile);
        if (landing == null || landing.point() == null || landing.foothold() == null) {
            return false;
        }

        AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(map, AgentBotMovementStateRuntime.movementProfile(entry));
        if (graph == null) {
            return landing.foothold().getId() == currentFh.getId();
        }

        int currentRegionId = BotNavigationManager.resolveCurrentRegionId(graph, entry, map, botPos);
        int landingRegionId = BotNavigationManager.resolveTargetRegionId(graph, entry, map, landing.point());
        return currentRegionId >= 0 && currentRegionId == landingRegionId;
    }

    private static Rectangle inclusiveRectangle(int left, int top, int right, int bottom) {
        return new Rectangle(left, top, Math.max(1, right - left + 1), Math.max(1, bottom - top + 1));
    }

    private static boolean isDirectionalDropEdge(AgentNavigationGraph.Edge navEdge) {
        return navEdge != null
                && navEdge.type == AgentNavigationGraph.EdgeType.DROP
                && navEdge.launchStepX != 0;
    }

    public static int resolveGroundStepX(BotEntry entry, Point botPos, Point targetPos, int stopDist, int followDist) {
        return AgentGroundMovementService.resolveGroundStepX(entry, botPos, targetPos, stopDist, followDist);
    }

    private static void applyGroundAction(BotEntry entry, Foothold currentFh, MoveAction action) {
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        AgentBotMovementStateRuntime.setMoveDirection(entry, switch (action.type()) {
            case WALK, JUMP -> Integer.compare(action.stepX(), 0);
            default -> 0;
        });

        if (action.type() == ActionType.CROUCH) {
            BotPhysicsEngine.queueDownJump(entry, bot);
            broadcastMovement(entry);
            return;
        }
        if (action.type() == ActionType.JUMP) {
            initiateFixedArcJump(entry, bot, action.stepX());
            return;
        }

        BotPhysicsEngine.GroundMotion motion =
                BotPhysicsEngine.applyGroundMotion(entry, bot, currentFh);
        if (motion.lostGround()) {
            broadcastMovement(entry);
            return;
        }

        if (motion.stepX() == 0) {
            applyIdleOrInPlaceMotion(entry, action);
            return;
        }

        broadcastMovement(entry);
    }

    private static void applyIdleOrInPlaceMotion(BotEntry entry, MoveAction action) {
        // Preserve ground momentum while still trying to walk/jump toward a nav target.
        // Otherwise subpixel uphill/transition movement gets zeroed every tick and the bot
        // can stall forever short of a valid launch window.
        if (AgentBotMovementStateRuntime.movementVelocityX(entry) == 0 && action.type() == ActionType.IDLE) {
            BotPhysicsEngine.idleOnGround(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
        }
        broadcastMovement(entry);
    }

    private static void performDownJump(BotEntry entry) {
        BotPhysicsEngine.beginDownJump(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
        broadcastMovement(entry);
    }

    private static void performTopRopeEntry(BotEntry entry) {
        BotPhysicsEngine.beginTopRopeEntry(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
        broadcastMovement(entry);
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

    private static void initiateFixedArcJump(BotEntry entry, Character bot, int dx) {
        initiateJump(entry, bot, dx);
        AgentBotMovementPhysicsStateRuntime.setFixedAirArc(entry, true);
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

    private static JumpLanding wrapLanding(BotPhysicsEngine.JumpLanding landing) {
        if (landing == null) {
            return null;
        }
        return new JumpLanding(landing.point(), landing.foothold());
    }
}
