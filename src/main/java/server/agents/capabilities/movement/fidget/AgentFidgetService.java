package server.agents.capabilities.movement.fidget;

import server.agents.capabilities.movement.AgentJumpActionService;
import server.agents.capabilities.movement.AgentGroundCollisionService;
import server.agents.capabilities.movement.AgentMovementPhaseDispatchService;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.capabilities.movement.AgentGroundPhysicsService;
import server.agents.capabilities.movement.AgentGroundingService;

import server.agents.capabilities.movement.AgentMovementBroadcastService;

import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentMovementPoseService;

import client.Character;
import net.packet.Packet;
import server.agents.integration.AgentFidgetRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.integration.AgentMovementStateRuntime;
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.runtime.AgentOwnerMotionStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentTickStateRuntime;
import server.agents.runtime.AgentRandom;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;
import tools.PacketCreator;

import java.awt.*;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class AgentFidgetService {
    private static final int SPAM_BASE_DELAY_MIN_MS = 100;
    private static final int SPAM_BASE_DELAY_MAX_MS = 250;
    private static final int SPAM_JITTER_MS = 50;

    private AgentFidgetService() {
    }

    public static boolean tryHandleTick(AgentRuntimeEntry entry, Point targetPos, boolean runAiTick) {
        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
        if (entry == null || bot == null || targetPos == null) {
            clear(entry);
            return false;
        }

        Point botPos = bot.getPosition();
        long now = System.currentTimeMillis();
        if (AgentFidgetStateRuntime.active(entry)) {
            if (!shouldKeepRunning(entry, botPos, targetPos, now)) {
                finishFidget(entry, botPos);
                return false;
            }
            return handleActiveTick(entry, botPos, targetPos, now);
        }

        if (!isEligible(entry, botPos, targetPos)) {
            return false;
        }

        if (runAiTick) {
            maybeRollIdleFidget(entry, botPos, targetPos, now);
        }
        if (AgentFidgetStateRuntime.inactive(entry)) {
            maybeStartSpeedMismatchFidget(entry, botPos, targetPos, now, runAiTick);
        }
        if (AgentFidgetStateRuntime.inactive(entry)) {
            return false;
        }

        return handleActiveTick(entry, botPos, targetPos, now);
    }

    public static void clear(AgentRuntimeEntry entry) {
        if (entry == null) {
            return;
        }

        AgentFidgetStateRuntime.clear(entry);
    }

    private static void finishFidget(AgentRuntimeEntry entry, Point botPos) {
        if (entry == null) {
            return;
        }

        AgentFidgetTrigger trigger = AgentFidgetStateRuntime.trigger(entry);
        Point origin = AgentFidgetStateRuntime.originPos(entry);
        clear(entry);
        if (shouldReturnToOrigin(trigger, origin, botPos)) {
            AgentMoveTargetStateRuntime.setPreciseMoveTarget(entry, origin);
            AgentMovementStateResetService.clearNavigationState(entry);
        }
    }

    private static boolean shouldReturnToOrigin(AgentFidgetTrigger trigger, Point origin, Point botPos) {
        if (trigger != AgentFidgetTrigger.IDLE && trigger != AgentFidgetTrigger.SOCIAL) {
            return false;
        }
        if (origin == null || botPos == null) {
            return false;
        }
        return Math.abs(botPos.x - origin.x) > 8 || Math.abs(botPos.y - origin.y) > 8;
    }

    public static void startFidget(AgentRuntimeEntry entry, AgentFidgetMode mode, long now, int durationMs) {
        startFidget(entry, mode, now, durationMs, AgentFidgetTrigger.AUTO_FOLLOW);
    }

    public static void startFidget(AgentRuntimeEntry entry,
                           AgentFidgetMode mode,
                           long now,
                           int durationMs,
                           AgentFidgetTrigger trigger) {
        if (entry == null || mode == null || mode == AgentFidgetMode.NONE) {
            return;
        }

        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
        int airSteerDir = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        boolean spamAirSteer = isJumpFidget(mode) && ThreadLocalRandom.current().nextInt(100) < 35;
        AgentFidgetStateRuntime.start(
                entry,
                mode,
                trigger == null ? AgentFidgetTrigger.AUTO_FOLLOW : trigger,
                now + Math.max(2000, durationMs),
                now,
                airSteerDir,
                spamAirSteer,
                randomActionBaseDelayMs(mode, spamAirSteer),
                bot == null ? null : new Point(bot.getPosition()),
                now + AgentRandom.randMs(500, 1200),
                now + AgentRandom.randMs(4000, 8000));
    }

    public static boolean maybeStartGreetingFidget(AgentRuntimeEntry entry, int roll) {
        if (roll >= 50) {
            return false;
        }
        return maybeStartSocialFidget(entry);
    }

    public static boolean maybeStartSocialFidget(AgentRuntimeEntry entry) {
        if (entry == null
                || AgentFidgetStateRuntime.active(entry)
                || !AgentModeStateRuntime.following(entry)
                || AgentFidgetRuntime.isLeaderIdleForFidget(entry)
                || AgentModeStateRuntime.grinding(entry)
                || AgentMoveTargetStateRuntime.hasMoveTarget(entry)
                || AgentNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)
                || AgentNavigationDebugStateRuntime.navPreciseTarget(entry)
                || AgentNavigationDebugStateRuntime.graphWarmupFallback(entry)
                || AgentMovementStateRuntime.inAir(entry)
                || AgentMovementStateRuntime.climbing(entry)) {
            return false;
        }

        startRandomFidget(entry, System.currentTimeMillis(), (int) AgentRandom.randMs(2000, 5000), AgentFidgetTrigger.SOCIAL);
        return true;
    }

    private static boolean isEligible(AgentRuntimeEntry entry, Point botPos, Point targetPos) {
        return isEligible(entry, botPos, targetPos, false);
    }

    private static boolean isEligible(AgentRuntimeEntry entry,
                                      Point botPos,
                                      Point targetPos,
                                      boolean allowAirborneJumpFidget) {
        boolean inAir = AgentMovementStateRuntime.inAir(entry);
        boolean airborneJumpFidget = inAir && allowAirborneJumpFidget
                && isJumpFidget(AgentFidgetStateRuntime.mode(entry));
        return AgentModeStateRuntime.following(entry)
                && !AgentFidgetRuntime.isLeaderIdleForFidget(entry)
                && !AgentModeStateRuntime.grinding(entry)
                && !AgentMoveTargetStateRuntime.hasMoveTarget(entry)
                && !AgentNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)
                && !AgentNavigationDebugStateRuntime.navPreciseTarget(entry)
                && !AgentNavigationDebugStateRuntime.graphWarmupFallback(entry)
                && !AgentMovementStateRuntime.climbing(entry)
                && (!inAir || airborneJumpFidget)
                && !AgentMovementStateRuntime.downJumpPending(entry)
                && (airborneJumpFidget || Math.abs(targetPos.y - botPos.y) <= AgentMovementPhysicsConfig.configuredJumpYThreshold() * 2);
    }

    private static boolean shouldKeepRunning(AgentRuntimeEntry entry, Point botPos, Point targetPos, long now) {
        if (!isEligible(entry, botPos, targetPos, true) || AgentFidgetStateRuntime.expired(entry, now)) {
            return false;
        }
        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
        if (bot == null) {
            return false;
        }
        int walkStep = AgentMovementKinematicsService.walkStep(bot.getMap(), AgentMovementStateRuntime.movementProfile(entry));
        int absDx = Math.abs(targetPos.x - botPos.x);
        return absDx <= AgentMovementPhysicsConfig.configuredFollowDist() + walkStep * 3;
    }

    private static boolean isJumpFidget(AgentFidgetMode mode) {
        return mode == AgentFidgetMode.JUMP || mode == AgentFidgetMode.DIAGONAL_JUMP;
    }

    private static void maybeRollIdleFidget(AgentRuntimeEntry entry, Point botPos, Point targetPos, long now) {
        if (!AgentTickStateRuntime.lastTickWasAi(entry) || !isOwnerMostlyIdle(entry)) {
            return;
        }
        if (Math.abs(targetPos.x - botPos.x) > AgentMovementPhysicsConfig.configuredFollowDist()) {
            return;
        }
        if (AgentFidgetStateRuntime.idleRollNotScheduled(entry)) {
            AgentFidgetStateRuntime.setNextIdleRollAtMs(entry, now + AgentRandom.randMs(30_000, 60_000));
            return;
        }
        if (!AgentFidgetStateRuntime.idleRollDue(entry, now)) {
            return;
        }

        AgentFidgetStateRuntime.setNextIdleRollAtMs(entry, now + AgentRandom.randMs(30_000, 60_000));
        if (ThreadLocalRandom.current().nextInt(100) >= 20) {
            return;
        }

        startRandomFidget(entry, now, (int) AgentRandom.randMs(2000, 10_000), AgentFidgetTrigger.IDLE);
    }

    private static void maybeStartSpeedMismatchFidget(AgentRuntimeEntry entry, Point botPos, Point targetPos, long now, boolean runAiTick) {
        if (!runAiTick || !AgentFidgetStateRuntime.nextFidgetDue(entry, now)) {
            return;
        }
        if (!shouldStartSpeedMismatchFidget(entry, botPos, targetPos)) {
            return;
        }

        AgentFidgetStateRuntime.setNextFidgetAtMs(entry, now + AgentRandom.randMs(1500, 3000));
        if (ThreadLocalRandom.current().nextInt(100) >= 35) {
            return;
        }

        startRandomFidget(entry, now, (int) AgentRandom.randMs(2000, 4500), AgentFidgetTrigger.AUTO_FOLLOW);
    }

    public static boolean shouldStartSpeedMismatchFidget(AgentRuntimeEntry entry, Point botPos, Point targetPos) {
        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
        if (entry == null || bot == null || botPos == null || targetPos == null) {
            return false;
        }
        if (isOwnerMostlyIdle(entry)) {
            return false;
        }

        int walkStep = AgentMovementKinematicsService.walkStep(bot.getMap(), AgentMovementStateRuntime.movementProfile(entry));
        int absDx = Math.abs(targetPos.x - botPos.x);
        int ownerStep = AgentOwnerMotionStateRuntime.maxObservedOwnerStep(entry);
        return absDx <= AgentMovementPhysicsConfig.configuredFollowDist() + walkStep
                && ownerStep < walkStep;
    }

    private static boolean isOwnerMostlyIdle(AgentRuntimeEntry entry) {
        return AgentOwnerMotionStateRuntime.ownerMostlyIdle(entry);
    }

    public static void startRandomFidget(AgentRuntimeEntry entry, long now, int durationMs) {
        startRandomFidget(entry, now, durationMs, AgentFidgetTrigger.AUTO_FOLLOW);
    }

    public static void startRandomFidget(AgentRuntimeEntry entry, long now, int durationMs, AgentFidgetTrigger trigger) {
        AgentFidgetMode mode = switch (ThreadLocalRandom.current().nextInt(6)) {
            case 0 -> AgentFidgetMode.WAIT;
            case 1 -> AgentFidgetMode.JUMP;
            case 2 -> AgentFidgetMode.DIAGONAL_JUMP;
            case 3 -> AgentFidgetMode.PRONE;
            case 4 -> AgentFidgetMode.SPAM_PRONE;
            default -> AgentFidgetMode.SPAM_SIDEWAYS;
        };
        startFidget(entry, mode, now, durationMs, trigger);
    }

    private static boolean handleActiveTick(AgentRuntimeEntry entry, Point botPos, Point targetPos, long now) {
        if (AgentMovementStateRuntime.climbing(entry)) {
            finishFidget(entry, botPos);
            return false;
        }
        if (AgentMovementStateRuntime.inAir(entry)) {
            tickActiveAirborne(entry, now);
            AgentMovementPhaseDispatchService.tickAirborne(entry, null);
            return true;
        }
        return executeGrounded(entry, botPos, targetPos, now);
    }

    private static void tickActiveAirborne(AgentRuntimeEntry entry, long now) {
        if (!AgentFidgetStateRuntime.modeIsAny(entry, AgentFidgetMode.JUMP, AgentFidgetMode.DIAGONAL_JUMP)
                || !AgentFidgetStateRuntime.actionDue(entry, now)) {
            return;
        }
        if (!AgentFidgetStateRuntime.spamAirSteer(entry)) {
            return;
        }

        int steerDir = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        AgentFidgetStateRuntime.setAirSteerDir(entry, steerDir);
        AgentMovementStateRuntime.setMoveDirection(entry, steerDir);
        AgentFidgetStateRuntime.setNextActionAtMs(
                entry,
                now + jitteredDelayMs(AgentFidgetStateRuntime.actionBaseDelayMs(entry)));
    }

    private static boolean executeGrounded(AgentRuntimeEntry entry, Point botPos, Point targetPos, long now) {
        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
        if (bot == null) {
            return false;
        }
        return switch (AgentFidgetStateRuntime.mode(entry)) {
            case WAIT -> {
                AgentMovementPoseService.idleOnGround(entry, bot);
                AgentMovementBroadcastService.broadcastMovement(entry);
                yield true;
            }
            case PRONE -> {
                AgentMovementPoseService.proneOnGround(entry, bot);
                maybeBroadcastProneAttackVisual(entry, now);
                AgentMovementBroadcastService.broadcastMovement(entry);
                yield true;
            }
            case SPAM_PRONE -> {
                if (AgentFidgetStateRuntime.actionDue(entry, now)) {
                    if (AgentFidgetStateRuntime.crouching(entry)) {
                        AgentMovementPoseService.idleOnGround(entry, bot);
                    } else {
                        AgentMovementPoseService.proneOnGround(entry, bot);
                    }
                    AgentMovementBroadcastService.broadcastMovement(entry);
                    AgentFidgetStateRuntime.setNextActionAtMs(entry, now + AgentRandom.randMs(120, 350));
                }
                maybeBroadcastProneAttackVisual(entry, now);
                yield true;
            }
            case SPAM_SIDEWAYS -> {
                tickSidewaysMovement(entry, bot, botPos, now);
                yield true;
            }
            case JUMP -> {
                if (AgentFidgetStateRuntime.jumpDue(entry, now)) {
                    initiateFidgetJump(entry, bot, botPos, targetPos, now, false);
                } else {
                    AgentMovementPoseService.idleOnGround(entry, bot);
                    AgentMovementBroadcastService.broadcastMovement(entry);
                }
                yield true;
            }
            case DIAGONAL_JUMP -> {
                if (AgentFidgetStateRuntime.jumpDue(entry, now)) {
                    initiateFidgetJump(entry, bot, botPos, targetPos, now, true);
                } else {
                    AgentMovementPoseService.idleOnGround(entry, bot);
                    AgentMovementBroadcastService.broadcastMovement(entry);
                }
                yield true;
            }
            default -> false;
        };
    }

    private static void initiateFidgetJump(AgentRuntimeEntry entry,
                                          Character bot,
                                          Point botPos,
                                          Point targetPos,
                                          long now,
                                          boolean diagonal) {
        int walkStep = AgentMovementKinematicsService.walkStep(bot.getMap(), AgentMovementStateRuntime.movementProfile(entry));
        int jumpDx;
        if (diagonal) {
            int jumpDir = nextDiagonalJumpDir(entry, bot, botPos);
            jumpDx = jumpDir * walkStep;
            AgentFidgetStateRuntime.setJumpDir(entry, -jumpDir);
            AgentFidgetStateRuntime.setAirSteerDir(entry, jumpDir);
        } else {
            int dx = targetPos.x - botPos.x;
            jumpDx = switch (ThreadLocalRandom.current().nextInt(3)) {
                case 0 -> 0;
                case 1 -> dx == 0 ? walkStep : Integer.signum(dx) * walkStep;
                default -> (ThreadLocalRandom.current().nextBoolean() ? 1 : -1) * walkStep;
            };
            AgentFidgetStateRuntime.setAirSteerDir(entry, Integer.signum(jumpDx));
        }

        AgentJumpActionService.initiateJump(entry, bot, jumpDx);
        AgentFidgetStateRuntime.setNextJumpAtMs(entry, now + AgentRandom.randMs(200, 400));
    }

    private static int nextDiagonalJumpDir(AgentRuntimeEntry entry, Character bot, Point botPos) {
        Point origin = AgentFidgetStateRuntime.originPos(entry);
        if (origin != null && botPos != null) {
            int dxFromOrigin = botPos.x - origin.x;
            int bias = Math.max(8, AgentMovementKinematicsService.walkStep(bot.getMap(), AgentMovementStateRuntime.movementProfile(entry)));
            if (dxFromOrigin >= bias) {
                return -1;
            }
            if (dxFromOrigin <= -bias) {
                return 1;
            }
        }
        return AgentFidgetStateRuntime.jumpDir(entry) == 0
                ? (ThreadLocalRandom.current().nextBoolean() ? 1 : -1)
                : AgentFidgetStateRuntime.jumpDir(entry);
    }

    private static void tickSidewaysMovement(AgentRuntimeEntry entry, Character bot, Point botPos, long now) {
        if (AgentFidgetStateRuntime.actionDue(entry, now) || AgentFidgetStateRuntime.moveDir(entry) == 0) {
            AgentFidgetStateRuntime.setMoveDir(entry, nextSidewaysDir(entry, bot, botPos));
            AgentFidgetStateRuntime.setNextActionAtMs(
                    entry,
                    now + jitteredDelayMs(AgentFidgetStateRuntime.actionBaseDelayMs(entry)));
        }

        int dir = AgentFidgetStateRuntime.moveDir(entry) == 0 ? 1 : AgentFidgetStateRuntime.moveDir(entry);
        int walkStep = AgentMovementKinematicsService.walkStep(bot.getMap(), AgentMovementStateRuntime.movementProfile(entry));
        if (!AgentGroundCollisionService.canWalkGroundStep(bot.getMap(), botPos, dir * walkStep)) {
            dir = -dir;
            AgentFidgetStateRuntime.setMoveDir(entry, dir);
            if (!AgentGroundCollisionService.canWalkGroundStep(bot.getMap(), botPos, dir * walkStep)) {
                AgentMovementPoseService.idleOnGround(entry, bot);
                AgentMovementBroadcastService.broadcastMovement(entry);
                return;
            }
        }

        Foothold currentFh = AgentGroundingService.findGroundFoothold(bot.getMap(), botPos);
        if (currentFh == null) {
            AgentMovementBroadcastService.broadcastMovement(entry);
            return;
        }

        AgentMovementStateRuntime.setMoveDirection(entry, dir);
        AgentGroundPhysicsService.applyGroundMotion(entry, bot, currentFh);
        AgentMovementBroadcastService.broadcastMovement(entry);
    }

    private static int nextSidewaysDir(AgentRuntimeEntry entry, Character bot, Point botPos) {
        Point origin = AgentFidgetStateRuntime.originPos(entry);
        int walkStep = AgentMovementKinematicsService.walkStep(bot.getMap(), AgentMovementStateRuntime.movementProfile(entry));
        if (origin != null && botPos != null) {
            int dxFromOrigin = botPos.x - origin.x;
            int bound = Math.max(12, walkStep * 2);
            if (dxFromOrigin >= bound) {
                return -1;
            }
            if (dxFromOrigin <= -bound) {
                return 1;
            }
        }
        return AgentFidgetStateRuntime.moveDir(entry) == 0
                ? (ThreadLocalRandom.current().nextBoolean() ? 1 : -1)
                : -AgentFidgetStateRuntime.moveDir(entry);
    }

    private static int randomActionBaseDelayMs(AgentFidgetMode mode, boolean spamAirSteer) {
        if (mode == AgentFidgetMode.SPAM_SIDEWAYS) {
            return randomTickAlignedBaseDelayMs();
        }
        if (isJumpFidget(mode) && spamAirSteer) {
            return randomTickAlignedBaseDelayMs();
        }
        return 0;
    }

    private static int randomTickAlignedBaseDelayMs() {
        int movementTickMs = AgentMovementPhysicsConfig.configuredMovementTickMs();
        int ticks = ThreadLocalRandom.current().nextInt(SPAM_BASE_DELAY_MIN_MS / movementTickMs,
                SPAM_BASE_DELAY_MAX_MS / movementTickMs + 1);
        return ticks * movementTickMs;
    }

    private static long jitteredDelayMs(int baseDelayMs) {
        int base = baseDelayMs > 0 ? baseDelayMs : SPAM_BASE_DELAY_MIN_MS;
        return base + (ThreadLocalRandom.current().nextBoolean() ? SPAM_JITTER_MS : 0);
    }

    private static void maybeBroadcastProneAttackVisual(AgentRuntimeEntry entry, long now) {
        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
        if (entry == null || bot == null || bot.getMap() == null
                || !AgentFidgetStateRuntime.crouching(entry)
                || !AgentFidgetStateRuntime.visualDue(entry, now)) {
            return;
        }

        AgentFidgetStateRuntime.setNextVisualAtMs(entry, now + AgentRandom.randMs(700, 1600));
        if (ThreadLocalRandom.current().nextInt(100) >= 35) {
            return;
        }

        int direction = AgentAttackExecutionProvider.bodyActionId("proneStab", "stabO1", null);
        Packet attackPacket = PacketCreator.closeRangeAttack(
                bot,
                0,
                0,
                AgentMovementStateRuntime.facingDirection(entry) < 0 ? 0x80 : 0x00,
                0,
                Map.of(),
                4,
                direction,
                0);
        bot.getMap().broadcastMessage(bot, attackPacket, false);
    }
}
