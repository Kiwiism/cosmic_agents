package server.bots;

import client.Character;
import net.packet.Packet;
import server.agents.capabilities.movement.fidget.AgentFidgetMode;
import server.agents.capabilities.movement.fidget.AgentFidgetTrigger;
import server.agents.integration.AgentBotFidgetRuntime;
import server.agents.integration.AgentBotFidgetStateRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotOwnerMotionStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.integration.AgentBotTickStateRuntime;
import server.maps.Foothold;
import tools.PacketCreator;

import java.awt.*;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

final class BotFidgetManager {
    private static final int SPAM_BASE_DELAY_MIN_MS = 100;
    private static final int SPAM_BASE_DELAY_MAX_MS = 250;
    private static final int SPAM_JITTER_MS = 50;

    private BotFidgetManager() {
    }

    static boolean tryHandleTick(BotEntry entry, Point targetPos, boolean runAiTick) {
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        if (entry == null || bot == null || targetPos == null) {
            clear(entry);
            return false;
        }

        Point botPos = bot.getPosition();
        long now = System.currentTimeMillis();
        if (AgentBotFidgetStateRuntime.active(entry)) {
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
        if (AgentBotFidgetStateRuntime.inactive(entry)) {
            maybeStartSpeedMismatchFidget(entry, botPos, targetPos, now, runAiTick);
        }
        if (AgentBotFidgetStateRuntime.inactive(entry)) {
            return false;
        }

        return handleActiveTick(entry, botPos, targetPos, now);
    }

    static void clear(BotEntry entry) {
        if (entry == null) {
            return;
        }

        AgentBotFidgetStateRuntime.clear(entry);
    }

    private static void finishFidget(BotEntry entry, Point botPos) {
        if (entry == null) {
            return;
        }

        AgentFidgetTrigger trigger = AgentBotFidgetStateRuntime.trigger(entry);
        Point origin = AgentBotFidgetStateRuntime.originPos(entry);
        clear(entry);
        if (shouldReturnToOrigin(trigger, origin, botPos)) {
            AgentBotMoveTargetStateRuntime.setPreciseMoveTarget(entry, origin);
            BotMovementManager.clearNavigationState(entry);
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

    static void startFidget(BotEntry entry, AgentFidgetMode mode, long now, int durationMs) {
        startFidget(entry, mode, now, durationMs, AgentFidgetTrigger.AUTO_FOLLOW);
    }

    static void startFidget(BotEntry entry,
                           AgentFidgetMode mode,
                           long now,
                           int durationMs,
                           AgentFidgetTrigger trigger) {
        if (entry == null || mode == null || mode == AgentFidgetMode.NONE) {
            return;
        }

        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        int airSteerDir = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        boolean spamAirSteer = isJumpFidget(mode) && ThreadLocalRandom.current().nextInt(100) < 35;
        AgentBotFidgetStateRuntime.start(
                entry,
                mode,
                trigger == null ? AgentFidgetTrigger.AUTO_FOLLOW : trigger,
                now + Math.max(2000, durationMs),
                now,
                airSteerDir,
                spamAirSteer,
                randomActionBaseDelayMs(mode, spamAirSteer),
                bot == null ? null : new Point(bot.getPosition()),
                now + BotManager.randMs(500, 1200),
                now + BotManager.randMs(4000, 8000));
    }

    static boolean maybeStartGreetingFidget(BotEntry entry, int roll) {
        if (roll >= 50) {
            return false;
        }
        return maybeStartSocialFidget(entry);
    }

    static boolean maybeStartSocialFidget(BotEntry entry) {
        if (entry == null
                || AgentBotFidgetStateRuntime.active(entry)
                || !AgentBotModeStateRuntime.following(entry)
                || AgentBotFidgetRuntime.isLeaderIdleForFidget(entry)
                || AgentBotModeStateRuntime.grinding(entry)
                || AgentBotMoveTargetStateRuntime.hasMoveTarget(entry)
                || AgentBotNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)
                || AgentBotNavigationDebugStateRuntime.navPreciseTarget(entry)
                || AgentBotNavigationDebugStateRuntime.graphWarmupFallback(entry)
                || AgentBotMovementStateRuntime.inAir(entry)
                || AgentBotMovementStateRuntime.climbing(entry)) {
            return false;
        }

        startRandomFidget(entry, System.currentTimeMillis(), (int) BotManager.randMs(2000, 5000), AgentFidgetTrigger.SOCIAL);
        return true;
    }

    private static boolean isEligible(BotEntry entry, Point botPos, Point targetPos) {
        return isEligible(entry, botPos, targetPos, false);
    }

    private static boolean isEligible(BotEntry entry,
                                      Point botPos,
                                      Point targetPos,
                                      boolean allowAirborneJumpFidget) {
        boolean inAir = AgentBotMovementStateRuntime.inAir(entry);
        boolean airborneJumpFidget = inAir && allowAirborneJumpFidget
                && isJumpFidget(AgentBotFidgetStateRuntime.mode(entry));
        return AgentBotModeStateRuntime.following(entry)
                && !AgentBotFidgetRuntime.isLeaderIdleForFidget(entry)
                && !AgentBotModeStateRuntime.grinding(entry)
                && !AgentBotMoveTargetStateRuntime.hasMoveTarget(entry)
                && !AgentBotNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)
                && !AgentBotNavigationDebugStateRuntime.navPreciseTarget(entry)
                && !AgentBotNavigationDebugStateRuntime.graphWarmupFallback(entry)
                && !AgentBotMovementStateRuntime.climbing(entry)
                && (!inAir || airborneJumpFidget)
                && !AgentBotMovementStateRuntime.downJumpPending(entry)
                && (airborneJumpFidget || Math.abs(targetPos.y - botPos.y) <= BotMovementManager.cfg.JUMP_Y_THRESH * 2);
    }

    private static boolean shouldKeepRunning(BotEntry entry, Point botPos, Point targetPos, long now) {
        if (!isEligible(entry, botPos, targetPos, true) || AgentBotFidgetStateRuntime.expired(entry, now)) {
            return false;
        }
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        if (bot == null) {
            return false;
        }
        int walkStep = BotPhysicsEngine.walkStep(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry));
        int absDx = Math.abs(targetPos.x - botPos.x);
        return absDx <= BotMovementManager.cfg.FOLLOW_DIST + walkStep * 3;
    }

    private static boolean isJumpFidget(AgentFidgetMode mode) {
        return mode == AgentFidgetMode.JUMP || mode == AgentFidgetMode.DIAGONAL_JUMP;
    }

    private static void maybeRollIdleFidget(BotEntry entry, Point botPos, Point targetPos, long now) {
        if (!AgentBotTickStateRuntime.lastTickWasAi(entry) || !isOwnerMostlyIdle(entry)) {
            return;
        }
        if (Math.abs(targetPos.x - botPos.x) > BotMovementManager.cfg.FOLLOW_DIST) {
            return;
        }
        if (AgentBotFidgetStateRuntime.idleRollNotScheduled(entry)) {
            AgentBotFidgetStateRuntime.setNextIdleRollAtMs(entry, now + BotManager.randMs(30_000, 60_000));
            return;
        }
        if (!AgentBotFidgetStateRuntime.idleRollDue(entry, now)) {
            return;
        }

        AgentBotFidgetStateRuntime.setNextIdleRollAtMs(entry, now + BotManager.randMs(30_000, 60_000));
        if (ThreadLocalRandom.current().nextInt(100) >= 20) {
            return;
        }

        startRandomFidget(entry, now, (int) BotManager.randMs(2000, 10_000), AgentFidgetTrigger.IDLE);
    }

    private static void maybeStartSpeedMismatchFidget(BotEntry entry, Point botPos, Point targetPos, long now, boolean runAiTick) {
        if (!runAiTick || !AgentBotFidgetStateRuntime.nextFidgetDue(entry, now)) {
            return;
        }
        if (!shouldStartSpeedMismatchFidget(entry, botPos, targetPos)) {
            return;
        }

        AgentBotFidgetStateRuntime.setNextFidgetAtMs(entry, now + BotManager.randMs(1500, 3000));
        if (ThreadLocalRandom.current().nextInt(100) >= 35) {
            return;
        }

        startRandomFidget(entry, now, (int) BotManager.randMs(2000, 4500), AgentFidgetTrigger.AUTO_FOLLOW);
    }

    static boolean shouldStartSpeedMismatchFidget(BotEntry entry, Point botPos, Point targetPos) {
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        if (entry == null || bot == null || botPos == null || targetPos == null) {
            return false;
        }
        if (isOwnerMostlyIdle(entry)) {
            return false;
        }

        int walkStep = BotPhysicsEngine.walkStep(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry));
        int absDx = Math.abs(targetPos.x - botPos.x);
        int ownerStep = AgentBotOwnerMotionStateRuntime.maxObservedOwnerStep(entry);
        return absDx <= BotMovementManager.cfg.FOLLOW_DIST + walkStep
                && ownerStep < walkStep;
    }

    private static boolean isOwnerMostlyIdle(BotEntry entry) {
        return AgentBotOwnerMotionStateRuntime.ownerMostlyIdle(entry);
    }

    static void startRandomFidget(BotEntry entry, long now, int durationMs) {
        startRandomFidget(entry, now, durationMs, AgentFidgetTrigger.AUTO_FOLLOW);
    }

    static void startRandomFidget(BotEntry entry, long now, int durationMs, AgentFidgetTrigger trigger) {
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

    private static boolean handleActiveTick(BotEntry entry, Point botPos, Point targetPos, long now) {
        if (AgentBotMovementStateRuntime.climbing(entry)) {
            finishFidget(entry, botPos);
            return false;
        }
        if (AgentBotMovementStateRuntime.inAir(entry)) {
            tickActiveAirborne(entry, now);
            BotMovementManager.tickAirborne(entry, null);
            return true;
        }
        return executeGrounded(entry, botPos, targetPos, now);
    }

    private static void tickActiveAirborne(BotEntry entry, long now) {
        if (!AgentBotFidgetStateRuntime.modeIsAny(entry, AgentFidgetMode.JUMP, AgentFidgetMode.DIAGONAL_JUMP)
                || !AgentBotFidgetStateRuntime.actionDue(entry, now)) {
            return;
        }
        if (!AgentBotFidgetStateRuntime.spamAirSteer(entry)) {
            return;
        }

        int steerDir = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        AgentBotFidgetStateRuntime.setAirSteerDir(entry, steerDir);
        AgentBotMovementStateRuntime.setMoveDirection(entry, steerDir);
        AgentBotFidgetStateRuntime.setNextActionAtMs(
                entry,
                now + jitteredDelayMs(AgentBotFidgetStateRuntime.actionBaseDelayMs(entry)));
    }

    private static boolean executeGrounded(BotEntry entry, Point botPos, Point targetPos, long now) {
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        if (bot == null) {
            return false;
        }
        return switch (AgentBotFidgetStateRuntime.mode(entry)) {
            case WAIT -> {
                BotPhysicsEngine.idleOnGround(entry, bot);
                BotMovementManager.broadcastMovement(entry);
                yield true;
            }
            case PRONE -> {
                BotPhysicsEngine.proneOnGround(entry, bot);
                maybeBroadcastProneAttackVisual(entry, now);
                BotMovementManager.broadcastMovement(entry);
                yield true;
            }
            case SPAM_PRONE -> {
                if (AgentBotFidgetStateRuntime.actionDue(entry, now)) {
                    if (AgentBotFidgetStateRuntime.crouching(entry)) {
                        BotPhysicsEngine.idleOnGround(entry, bot);
                    } else {
                        BotPhysicsEngine.proneOnGround(entry, bot);
                    }
                    BotMovementManager.broadcastMovement(entry);
                    AgentBotFidgetStateRuntime.setNextActionAtMs(entry, now + BotManager.randMs(120, 350));
                }
                maybeBroadcastProneAttackVisual(entry, now);
                yield true;
            }
            case SPAM_SIDEWAYS -> {
                tickSidewaysMovement(entry, bot, botPos, now);
                yield true;
            }
            case JUMP -> {
                if (AgentBotFidgetStateRuntime.jumpDue(entry, now)) {
                    initiateFidgetJump(entry, bot, botPos, targetPos, now, false);
                } else {
                    BotPhysicsEngine.idleOnGround(entry, bot);
                    BotMovementManager.broadcastMovement(entry);
                }
                yield true;
            }
            case DIAGONAL_JUMP -> {
                if (AgentBotFidgetStateRuntime.jumpDue(entry, now)) {
                    initiateFidgetJump(entry, bot, botPos, targetPos, now, true);
                } else {
                    BotPhysicsEngine.idleOnGround(entry, bot);
                    BotMovementManager.broadcastMovement(entry);
                }
                yield true;
            }
            default -> false;
        };
    }

    private static void initiateFidgetJump(BotEntry entry,
                                          Character bot,
                                          Point botPos,
                                          Point targetPos,
                                          long now,
                                          boolean diagonal) {
        int walkStep = BotPhysicsEngine.walkStep(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry));
        int jumpDx;
        if (diagonal) {
            int jumpDir = nextDiagonalJumpDir(entry, bot, botPos);
            jumpDx = jumpDir * walkStep;
            AgentBotFidgetStateRuntime.setJumpDir(entry, -jumpDir);
            AgentBotFidgetStateRuntime.setAirSteerDir(entry, jumpDir);
        } else {
            int dx = targetPos.x - botPos.x;
            jumpDx = switch (ThreadLocalRandom.current().nextInt(3)) {
                case 0 -> 0;
                case 1 -> dx == 0 ? walkStep : Integer.signum(dx) * walkStep;
                default -> (ThreadLocalRandom.current().nextBoolean() ? 1 : -1) * walkStep;
            };
            AgentBotFidgetStateRuntime.setAirSteerDir(entry, Integer.signum(jumpDx));
        }

        BotMovementManager.initiateJump(entry, bot, jumpDx);
        AgentBotFidgetStateRuntime.setNextJumpAtMs(entry, now + BotManager.randMs(200, 400));
    }

    private static int nextDiagonalJumpDir(BotEntry entry, Character bot, Point botPos) {
        Point origin = AgentBotFidgetStateRuntime.originPos(entry);
        if (origin != null && botPos != null) {
            int dxFromOrigin = botPos.x - origin.x;
            int bias = Math.max(8, BotPhysicsEngine.walkStep(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry)));
            if (dxFromOrigin >= bias) {
                return -1;
            }
            if (dxFromOrigin <= -bias) {
                return 1;
            }
        }
        return AgentBotFidgetStateRuntime.jumpDir(entry) == 0
                ? (ThreadLocalRandom.current().nextBoolean() ? 1 : -1)
                : AgentBotFidgetStateRuntime.jumpDir(entry);
    }

    private static void tickSidewaysMovement(BotEntry entry, Character bot, Point botPos, long now) {
        if (AgentBotFidgetStateRuntime.actionDue(entry, now) || AgentBotFidgetStateRuntime.moveDir(entry) == 0) {
            AgentBotFidgetStateRuntime.setMoveDir(entry, nextSidewaysDir(entry, bot, botPos));
            AgentBotFidgetStateRuntime.setNextActionAtMs(
                    entry,
                    now + jitteredDelayMs(AgentBotFidgetStateRuntime.actionBaseDelayMs(entry)));
        }

        int dir = AgentBotFidgetStateRuntime.moveDir(entry) == 0 ? 1 : AgentBotFidgetStateRuntime.moveDir(entry);
        int walkStep = BotPhysicsEngine.walkStep(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry));
        if (!BotPhysicsEngine.canWalkGroundStep(bot.getMap(), botPos, dir * walkStep)) {
            dir = -dir;
            AgentBotFidgetStateRuntime.setMoveDir(entry, dir);
            if (!BotPhysicsEngine.canWalkGroundStep(bot.getMap(), botPos, dir * walkStep)) {
                BotPhysicsEngine.idleOnGround(entry, bot);
                BotMovementManager.broadcastMovement(entry);
                return;
            }
        }

        Foothold currentFh = BotPhysicsEngine.findGroundFoothold(bot.getMap(), botPos);
        if (currentFh == null) {
            BotMovementManager.broadcastMovement(entry);
            return;
        }

        AgentBotMovementStateRuntime.setMoveDirection(entry, dir);
        BotPhysicsEngine.applyGroundMotion(entry, bot, currentFh);
        BotMovementManager.broadcastMovement(entry);
    }

    private static int nextSidewaysDir(BotEntry entry, Character bot, Point botPos) {
        Point origin = AgentBotFidgetStateRuntime.originPos(entry);
        int walkStep = BotPhysicsEngine.walkStep(bot.getMap(), AgentBotMovementStateRuntime.movementProfile(entry));
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
        return AgentBotFidgetStateRuntime.moveDir(entry) == 0
                ? (ThreadLocalRandom.current().nextBoolean() ? 1 : -1)
                : -AgentBotFidgetStateRuntime.moveDir(entry);
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
        int ticks = ThreadLocalRandom.current().nextInt(SPAM_BASE_DELAY_MIN_MS / BotPhysicsEngine.cfg.TICK_MS,
                SPAM_BASE_DELAY_MAX_MS / BotPhysicsEngine.cfg.TICK_MS + 1);
        return ticks * BotPhysicsEngine.cfg.TICK_MS;
    }

    private static long jitteredDelayMs(int baseDelayMs) {
        int base = baseDelayMs > 0 ? baseDelayMs : SPAM_BASE_DELAY_MIN_MS;
        return base + (ThreadLocalRandom.current().nextBoolean() ? SPAM_JITTER_MS : 0);
    }

    private static void maybeBroadcastProneAttackVisual(BotEntry entry, long now) {
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        if (entry == null || bot == null || bot.getMap() == null
                || !AgentBotFidgetStateRuntime.crouching(entry)
                || !AgentBotFidgetStateRuntime.visualDue(entry, now)) {
            return;
        }

        AgentBotFidgetStateRuntime.setNextVisualAtMs(entry, now + BotManager.randMs(700, 1600));
        if (ThreadLocalRandom.current().nextInt(100) >= 35) {
            return;
        }

        int direction = BotAttackExecutionProvider.bodyActionId("proneStab", "stabO1", null);
        Packet attackPacket = PacketCreator.closeRangeAttack(
                bot,
                0,
                0,
                AgentBotMovementStateRuntime.facingDirection(entry) < 0 ? 0x80 : 0x00,
                0,
                Map.of(),
                4,
                direction,
                0);
        bot.getMap().broadcastMessage(bot, attackPacket, false);
    }
}
