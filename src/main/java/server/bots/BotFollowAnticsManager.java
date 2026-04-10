package server.bots;

import client.Character;

import java.awt.*;
import java.util.concurrent.ThreadLocalRandom;

enum BotFollowAnticMode {
    NONE,
    WAIT,
    JUMP,
    PRONE,
    SPAM_PRONE
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

        if (!isEligible(entry, botPos, targetPos)) {
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
        entry.followAnticUntilMs = 0L;
        entry.nextFollowAnticActionAtMs = 0L;
        entry.followAnticAirSteerDir = 0;
    }

    static void startAntic(BotEntry entry, BotFollowAnticMode mode, long now, int durationMs) {
        if (entry == null || mode == null || mode == BotFollowAnticMode.NONE) {
            return;
        }

        entry.followAnticMode = mode;
        entry.followAnticUntilMs = now + Math.max(2000, durationMs);
        entry.nextFollowAnticActionAtMs = now;
        entry.followAnticAirSteerDir = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
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

        startRandomAntic(entry, System.currentTimeMillis(), (int) BotManager.randMs(2000, 5000));
        return true;
    }

    private static boolean isEligible(BotEntry entry, Point botPos, Point targetPos) {
        return entry.following
                && !BotChatManager.isOwnerIdle(entry)
                && !entry.grinding
                && entry.moveTarget == null
                && entry.navEdge == null
                && !entry.navPreciseTarget
                && !entry.graphWarmupFallback
                && !entry.climbing
                && !entry.inAir
                && !entry.downJumpPending
                && entry.movementProfile.totalSpeedStat() > BotMovementProfile.BASE_TOTAL_STAT
                && Math.abs(targetPos.y - botPos.y) <= BotMovementManager.cfg.JUMP_Y_THRESH * 2;
    }

    private static boolean shouldKeepRunning(BotEntry entry, Point botPos, Point targetPos, long now) {
        if (!isEligible(entry, botPos, targetPos) || now >= entry.followAnticUntilMs) {
            return false;
        }
        int walkStep = BotPhysicsEngine.walkStep(entry.bot.getMap(), entry.movementProfile);
        int absDx = Math.abs(targetPos.x - botPos.x);
        return absDx <= BotMovementManager.cfg.FOLLOW_DIST + walkStep * 3;
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

        startRandomAntic(entry, now, (int) BotManager.randMs(2000, 10_000));
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

        startRandomAntic(entry, now, (int) BotManager.randMs(2000, 4500));
    }

    private static boolean isOwnerMostlyIdle(BotEntry entry) {
        return Math.abs(entry.observedOwnerStepX) <= 1 && Math.abs(entry.observedOwnerStepY) <= 1;
    }

    static void startRandomAntic(BotEntry entry, long now, int durationMs) {
        BotFollowAnticMode mode = switch (ThreadLocalRandom.current().nextInt(4)) {
            case 0 -> BotFollowAnticMode.WAIT;
            case 1 -> BotFollowAnticMode.JUMP;
            case 2 -> BotFollowAnticMode.PRONE;
            default -> BotFollowAnticMode.SPAM_PRONE;
        };
        startAntic(entry, mode, now, durationMs);
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
        if (entry.followAnticMode != BotFollowAnticMode.JUMP || now < entry.nextFollowAnticActionAtMs) {
            return;
        }

        int steerDir = entry.followAnticAirSteerDir;
        if (steerDir == 0 || ThreadLocalRandom.current().nextInt(100) < 35) {
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
                yield true;
            }
            case JUMP -> {
                if (now >= entry.nextFollowAnticActionAtMs) {
                    int walkStep = BotPhysicsEngine.walkStep(bot.getMap(), entry.movementProfile);
                    int dx = targetPos.x - botPos.x;
                    int jumpDx = switch (ThreadLocalRandom.current().nextInt(3)) {
                        case 0 -> 0;
                        case 1 -> dx == 0 ? walkStep : Integer.signum(dx) * walkStep;
                        default -> (ThreadLocalRandom.current().nextBoolean() ? 1 : -1) * walkStep;
                    };
                    BotMovementManager.initiateJump(entry, bot, jumpDx);
                    entry.nextFollowAnticActionAtMs = now + BotManager.randMs(450, 900);
                } else {
                    BotPhysicsEngine.idleOnGround(entry, bot);
                    BotMovementManager.broadcastMovement(entry);
                }
                yield true;
            }
            default -> false;
        };
    }
}
