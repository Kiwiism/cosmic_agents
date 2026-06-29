package server.agents.integration;

import client.Character;
import server.agents.capabilities.movement.AgentMovementMode;
import server.agents.capabilities.movement.AgentMovementSnapshot;
import server.bots.BotEntry;
import server.bots.BotMovementProfile;

import java.awt.Point;

/**
 * Agent-owned read-only movement state facade over temporary BotEntry state.
 */
public final class AgentBotMovementStateRuntime {
    private AgentBotMovementStateRuntime() {
    }

    public static AgentMovementSnapshot snapshot(BotEntry entry) {
        return new AgentMovementSnapshot(
                AgentBotModeStateRuntime.following(entry),
                AgentBotModeStateRuntime.grinding(entry),
                AgentBotModeStateRuntime.followTargetId(entry),
                AgentBotMoveTargetStateRuntime.moveTarget(entry),
                AgentBotMoveTargetStateRuntime.isPrecise(entry),
                AgentBotFarmAnchorStateRuntime.farmAnchor(entry),
                AgentBotFarmAnchorStateRuntime.farmAnchorMapId(entry),
                AgentBotPatrolStateRuntime.patrolRegionId(entry),
                AgentBotPatrolStateRuntime.patrolMapId(entry),
                AgentBotPatrolStateRuntime.patrolWanderTarget(entry),
                position(entry.bot()),
                position(entry.owner()),
                mode(entry));
    }

    public static AgentMovementMode mode(BotEntry entry) {
        if (AgentBotModeStateRuntime.grinding(entry)) {
            return AgentMovementMode.GRINDING;
        }
        if (AgentBotPatrolStateRuntime.hasPatrolRegion(entry)) {
            return AgentMovementMode.PATROLLING;
        }
        if (AgentBotFarmAnchorStateRuntime.hasFarmAnchor(entry)) {
            return AgentMovementMode.FARMING;
        }
        if (AgentBotMoveTargetStateRuntime.hasMoveTarget(entry)) {
            return AgentMovementMode.MOVING;
        }
        if (AgentBotModeStateRuntime.following(entry)) {
            return AgentMovementMode.FOLLOWING;
        }
        return AgentMovementMode.STOPPED;
    }

    public static BotMovementProfile movementProfile(BotEntry entry) {
        return entry.movementProfile();
    }

    public static BotMovementProfile movementProfileOrCharacter(BotEntry entry, Character bot) {
        if (entry == null) {
            return BotMovementProfile.fromCharacter(null);
        }
        BotMovementProfile profile = entry.movementProfile();
        return profile == null ? BotMovementProfile.fromCharacter(bot) : profile;
    }

    public static void setMovementProfile(BotEntry entry, BotMovementProfile movementProfile) {
        entry.setMovementProfile(movementProfile);
    }

    public static void refreshMovementProfile(BotEntry entry, Character bot) {
        entry.setMovementProfile(BotMovementProfile.fromCharacter(bot));
    }

    public static int moveDirection(BotEntry entry) {
        return entry.moveDirection();
    }

    public static boolean hasMoveDirection(BotEntry entry) {
        return entry.moveDirection() != 0;
    }

    public static void setMoveDirection(BotEntry entry, int moveDirection) {
        entry.setMoveDirection(moveDirection);
    }

    public static void clearMoveDirection(BotEntry entry) {
        entry.clearMoveDirection();
    }

    public static int facingDirection(BotEntry entry) {
        return entry.facingDirection();
    }

    public static int facingDirectionSign(BotEntry entry) {
        return entry.facingDirection() >= 0 ? 1 : -1;
    }

    public static void setFacingDirection(BotEntry entry, int facingDirection) {
        entry.setFacingDirection(facingDirection);
    }

    public static boolean inAir(BotEntry entry) {
        return entry.inAir();
    }

    public static void setInAir(BotEntry entry, boolean inAir) {
        entry.setInAir(inAir);
    }

    public static boolean grounded(BotEntry entry) {
        return !entry.inAir();
    }

    public static boolean climbing(BotEntry entry) {
        return entry.climbing();
    }

    public static boolean notClimbing(BotEntry entry) {
        return !entry.climbing();
    }

    public static boolean downJumpPending(BotEntry entry) {
        return entry.downJumpPending();
    }

    public static void setDownJumpPending(BotEntry entry, boolean downJumpPending) {
        entry.setDownJumpPending(downJumpPending);
    }

    public static boolean crouching(BotEntry entry) {
        return entry.crouching();
    }

    public static void setCrouching(BotEntry entry, boolean crouching) {
        entry.setCrouching(crouching);
    }

    public static boolean hasDownJumpPending(BotEntry entry) {
        return entry.downJumpPending();
    }

    public static boolean hasDownJumpGracePeriod(BotEntry entry) {
        return entry.downJumpGracePeriodMs() != 0L;
    }

    public static long downJumpGracePeriodMs(BotEntry entry) {
        return entry.downJumpGracePeriodMs();
    }

    public static void setDownJumpGracePeriodMs(BotEntry entry, long downJumpGracePeriodMs) {
        entry.setDownJumpGracePeriodMs(downJumpGracePeriodMs);
    }

    public static boolean wasMovingX(BotEntry entry) {
        return entry.wasMovingX();
    }

    public static void setWasMovingX(BotEntry entry, boolean wasMovingX) {
        entry.setWasMovingX(wasMovingX);
    }

    public static int movementVelocityX(BotEntry entry) {
        return entry.movementVelX();
    }

    public static int movementVelocityY(BotEntry entry) {
        return entry.movementVelY();
    }

    public static boolean hasMovementVelocity(BotEntry entry) {
        return entry.hasMovementVelocity();
    }

    public static void setMovementVelocity(BotEntry entry, int velocityX, int velocityY) {
        entry.setMovementVelocity(velocityX, velocityY);
    }

    private static Point position(Character character) {
        return character == null || character.getPosition() == null ? null : new Point(character.getPosition());
    }
}
