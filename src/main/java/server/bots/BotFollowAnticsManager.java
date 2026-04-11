package server.bots;

import client.Character;
import net.packet.Packet;
import tools.PacketCreator;

import java.awt.*;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

enum BotFollowAnticMode {
    NONE,
    WAIT,
    JUMP,
    DIAGONAL_JUMP,
    PRONE,
    SPAM_PRONE
}

enum BotFollowAnticTrigger {
    NONE,
    AUTO_FOLLOW,
    SOCIAL
}

final class BotFollowAnticsManager {
    private BotFollowAnticsManager() {
    }

    static boolean tryHandleTick(BotEntry entry, Point targetPos, boolean runAiTick) {
        if (entry == null || entry.bot == null || targetPos == null) {
            clear(entry);
            return false;
        }

        Point botPos = entry.bot.getPosition();
        long now = System.currentTimeMillis();
        if (entry.followAnticMode != BotFollowAnticMode.NONE) {
            if (!shouldKeepRunning(entry, botPos, targetPos, now)) {
                clear(entry);
                return false;
            }
            return handleActiveTick(entry, botPos, targetPos, now);
        }

        if (!isEligible(entry, botPos, targetPos, true)) {
            return false;
        }

        if (runAiTick) {
            maybeRollIdleAntic(entry, botPos, targetPos, now);
        }
        if (entry.followAnticMode == BotFollowAnticMode.NONE) {
            maybeStartSpeedMismatchAntic(entry, botPos, targetPos, now, runAiTick);
        }
        if (entry.followAnticMode == BotFollowAnticMode.NONE) {
            return false;
        }

        return handleActiveTick(entry, botPos, targetPos, now);
    }

    static void clear(BotEntry entry) {
        if (entry == null) {
            return;
        }

        entry.followAnticMode = BotFollowAnticMode.NONE;
        entry.followAnticTrigger = BotFollowAnticTrigger.NONE;
        entry.followAnticUntilMs = 0L;
        entry.nextFollowAnticActionAtMs = 0L;
        entry.followAnticAirSteerDir = 0;
        entry.followAnticJumpDir = 0;
        entry.nextFollowAnticVisualAtMs = 0L;
    }

    static void startAntic(BotEntry entry, BotFollowAnticMode mode, long now, int durationMs) {
        startAntic(entry, mode, now, durationMs, BotFollowAnticTrigger.AUTO_FOLLOW);
    }

    static void startAntic(BotEntry entry,
                           BotFollowAnticMode mode,
                           long now,
                           int durationMs,
                           BotFollowAnticTrigger trigger) {
        if (entry == null || mode == null || mode == BotFollowAnticMode.NONE) {
            return;
        }

        entry.followAnticMode = mode;
        entry.followAnticTrigger = trigger == null ? BotFollowAnticTrigger.AUTO_FOLLOW : trigger;
        entry.followAnticUntilMs = now + Math.max(2000, durationMs);
        entry.nextFollowAnticActionAtMs = now;
        entry.followAnticAirSteerDir = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        entry.followAnticJumpDir = entry.followAnticAirSteerDir == 0 ? 1 : entry.followAnticAirSteerDir;
        entry.nextFollowAnticVisualAtMs = now + BotManager.randMs(500, 1200);
        entry.nextFollowAnticAtMs = now + BotManager.randMs(4000, 8000);
    }

    static boolean maybeStartGreetingAntic(BotEntry entry, int roll) {
        if (entry == null
                || entry.followAnticMode != BotFollowAnticMode.NONE
                || !entry.following
                || BotChatManager.isOwnerIdle(entry)
                || entry.grinding
                || entry.moveTarget != null
                || entry.navEdge != null
                || entry.navPreciseTarget
                || entry.graphWarmupFallback
                || entry.inAir
                || entry.climbing
                || roll >= 50) {
            return false;
        }

        startRandomAntic(entry, System.currentTimeMillis(), (int) BotManager.randMs(2000, 5000), BotFollowAnticTrigger.SOCIAL);
        return true;
    }

    private static boolean isEligible(BotEntry entry, Point botPos, Point targetPos, boolean requireFastFollow) {
        return isEligible(entry, botPos, targetPos, requireFastFollow, false);
    }

    private static boolean isEligible(BotEntry entry,
                                      Point botPos,
                                      Point targetPos,
                                      boolean requireFastFollow,
                                      boolean allowAirborneJumpAntic) {
        return entry.following
                && !BotChatManager.isOwnerIdle(entry)
                && !entry.grinding
                && entry.moveTarget == null
                && entry.navEdge == null
                && !entry.navPreciseTarget
                && !entry.graphWarmupFallback
                && !entry.climbing
                && (!entry.inAir || (allowAirborneJumpAntic && isJumpAntic(entry.followAnticMode)))
                && !entry.downJumpPending
                && (!requireFastFollow || entry.movementProfile.totalSpeedStat() > BotMovementProfile.BASE_TOTAL_STAT)
                && Math.abs(targetPos.y - botPos.y) <= BotMovementManager.cfg.JUMP_Y_THRESH * 2;
    }

    private static boolean shouldKeepRunning(BotEntry entry, Point botPos, Point targetPos, long now) {
        boolean requireFastFollow = entry.followAnticTrigger != BotFollowAnticTrigger.SOCIAL;
        if (!isEligible(entry, botPos, targetPos, requireFastFollow, true) || now >= entry.followAnticUntilMs) {
            return false;
        }
        int walkStep = BotPhysicsEngine.walkStep(entry.bot.getMap(), entry.movementProfile);
        int absDx = Math.abs(targetPos.x - botPos.x);
        return absDx <= BotMovementManager.cfg.FOLLOW_DIST + walkStep * 3;
    }

    private static boolean isJumpAntic(BotFollowAnticMode mode) {
        return mode == BotFollowAnticMode.JUMP || mode == BotFollowAnticMode.DIAGONAL_JUMP;
    }

    private static void maybeRollIdleAntic(BotEntry entry, Point botPos, Point targetPos, long now) {
        if (!entry.lastTickWasAi || !isOwnerMostlyIdle(entry)) {
            return;
        }
        if (Math.abs(targetPos.x - botPos.x) > BotMovementManager.cfg.FOLLOW_DIST) {
            return;
        }
        if (entry.nextIdleFollowAnticRollAtMs == 0L) {
            entry.nextIdleFollowAnticRollAtMs = now + BotManager.randMs(30_000, 60_000);
            return;
        }
        if (now < entry.nextIdleFollowAnticRollAtMs) {
            return;
        }

        entry.nextIdleFollowAnticRollAtMs = now + BotManager.randMs(30_000, 60_000);
        if (ThreadLocalRandom.current().nextInt(100) >= 20) {
            return;
        }

        startRandomAntic(entry, now, (int) BotManager.randMs(2000, 10_000), BotFollowAnticTrigger.AUTO_FOLLOW);
    }

    private static void maybeStartSpeedMismatchAntic(BotEntry entry, Point botPos, Point targetPos, long now, boolean runAiTick) {
        if (!runAiTick || now < entry.nextFollowAnticAtMs) {
            return;
        }

        int walkStep = BotPhysicsEngine.walkStep(entry.bot.getMap(), entry.movementProfile);
        int absDx = Math.abs(targetPos.x - botPos.x);
        int ownerStep = Math.max(Math.abs(entry.observedOwnerStepX), Math.abs(entry.observedOwnerStepY));
        if (absDx > BotMovementManager.cfg.FOLLOW_DIST + walkStep) {
            return;
        }
        if (ownerStep >= walkStep) {
            return;
        }

        entry.nextFollowAnticAtMs = now + BotManager.randMs(1500, 3000);
        if (ThreadLocalRandom.current().nextInt(100) >= 35) {
            return;
        }

        startRandomAntic(entry, now, (int) BotManager.randMs(2000, 4500), BotFollowAnticTrigger.AUTO_FOLLOW);
    }

    private static boolean isOwnerMostlyIdle(BotEntry entry) {
        return Math.abs(entry.observedOwnerStepX) <= 1 && Math.abs(entry.observedOwnerStepY) <= 1;
    }

    static void startRandomAntic(BotEntry entry, long now, int durationMs) {
        startRandomAntic(entry, now, durationMs, BotFollowAnticTrigger.AUTO_FOLLOW);
    }

    static void startRandomAntic(BotEntry entry, long now, int durationMs, BotFollowAnticTrigger trigger) {
        BotFollowAnticMode mode = switch (ThreadLocalRandom.current().nextInt(5)) {
            case 0 -> BotFollowAnticMode.WAIT;
            case 1 -> BotFollowAnticMode.JUMP;
            case 2 -> BotFollowAnticMode.DIAGONAL_JUMP;
            case 3 -> BotFollowAnticMode.PRONE;
            default -> BotFollowAnticMode.SPAM_PRONE;
        };
        startAntic(entry, mode, now, durationMs, trigger);
    }

    private static boolean handleActiveTick(BotEntry entry, Point botPos, Point targetPos, long now) {
        if (entry.climbing) {
            clear(entry);
            return false;
        }
        if (entry.inAir) {
            tickActiveAirborne(entry, now);
            BotMovementManager.tickAirborne(entry, null);
            return true;
        }
        return executeGrounded(entry, botPos, targetPos, now);
    }

    private static void tickActiveAirborne(BotEntry entry, long now) {
        if ((entry.followAnticMode != BotFollowAnticMode.JUMP
                && entry.followAnticMode != BotFollowAnticMode.DIAGONAL_JUMP)
                || now < entry.nextFollowAnticActionAtMs) {
            return;
        }

        int steerDir = entry.followAnticAirSteerDir;
        if (steerDir == 0 || (entry.followAnticMode == BotFollowAnticMode.JUMP
                && ThreadLocalRandom.current().nextInt(100) < 35)) {
            steerDir = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
            entry.followAnticAirSteerDir = steerDir;
        }
        BotPhysicsEngine.applyAirSteering(entry, steerDir * 30);
        entry.nextFollowAnticActionAtMs = now + BotManager.randMs(150, 350);
    }

    private static boolean executeGrounded(BotEntry entry, Point botPos, Point targetPos, long now) {
        Character bot = entry.bot;
        return switch (entry.followAnticMode) {
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
                if (now >= entry.nextFollowAnticActionAtMs) {
                    if (entry.crouching) {
                        BotPhysicsEngine.idleOnGround(entry, bot);
                    } else {
                        BotPhysicsEngine.proneOnGround(entry, bot);
                    }
                    BotMovementManager.broadcastMovement(entry);
                    entry.nextFollowAnticActionAtMs = now + BotManager.randMs(120, 350);
                }
                maybeBroadcastProneAttackVisual(entry, now);
                yield true;
            }
            case JUMP -> {
                if (now >= entry.nextFollowAnticActionAtMs) {
                    initiateAnticJump(entry, bot, botPos, targetPos, now, false);
                } else {
                    BotPhysicsEngine.idleOnGround(entry, bot);
                    BotMovementManager.broadcastMovement(entry);
                }
                yield true;
            }
            case DIAGONAL_JUMP -> {
                if (now >= entry.nextFollowAnticActionAtMs) {
                    initiateAnticJump(entry, bot, botPos, targetPos, now, true);
                } else {
                    BotPhysicsEngine.idleOnGround(entry, bot);
                    BotMovementManager.broadcastMovement(entry);
                }
                yield true;
            }
            default -> false;
        };
    }

    private static void initiateAnticJump(BotEntry entry,
                                          Character bot,
                                          Point botPos,
                                          Point targetPos,
                                          long now,
                                          boolean diagonal) {
        int walkStep = BotPhysicsEngine.walkStep(bot.getMap(), entry.movementProfile);
        int jumpDx;
        if (diagonal) {
            int jumpDir = entry.followAnticJumpDir == 0
                    ? (ThreadLocalRandom.current().nextBoolean() ? 1 : -1)
                    : entry.followAnticJumpDir;
            jumpDx = jumpDir * walkStep;
            entry.followAnticJumpDir = -jumpDir;
            entry.followAnticAirSteerDir = jumpDir;
        } else {
            int dx = targetPos.x - botPos.x;
            jumpDx = switch (ThreadLocalRandom.current().nextInt(3)) {
                case 0 -> 0;
                case 1 -> dx == 0 ? walkStep : Integer.signum(dx) * walkStep;
                default -> (ThreadLocalRandom.current().nextBoolean() ? 1 : -1) * walkStep;
            };
            entry.followAnticAirSteerDir = Integer.signum(jumpDx);
        }

        BotMovementManager.initiateJump(entry, bot, jumpDx);
        entry.nextFollowAnticActionAtMs = now + BotManager.randMs(150, 300);
    }

    private static void maybeBroadcastProneAttackVisual(BotEntry entry, long now) {
        if (entry == null || entry.bot == null || entry.bot.getMap() == null
                || !entry.crouching || now < entry.nextFollowAnticVisualAtMs) {
            return;
        }

        entry.nextFollowAnticVisualAtMs = now + BotManager.randMs(700, 1600);
        if (ThreadLocalRandom.current().nextInt(100) >= 35) {
            return;
        }

        Character bot = entry.bot;
        int direction = BotAttackExecutionProvider.bodyActionId("proneStab", "stabO1", null);
        Packet attackPacket = PacketCreator.closeRangeAttack(
                bot,
                0,
                0,
                entry.facingDir < 0 ? 0x80 : 0x00,
                0,
                Map.of(),
                4,
                direction,
                0);
        bot.getMap().broadcastMessage(bot, attackPacket, false);
    }
}
