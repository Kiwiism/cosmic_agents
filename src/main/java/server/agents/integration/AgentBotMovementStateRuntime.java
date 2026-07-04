package server.agents.integration;

import client.Character;
import server.agents.capabilities.movement.AgentMovementMode;
import server.agents.capabilities.movement.AgentMovementInputState;
import server.agents.capabilities.movement.AgentMovementSnapshot;
import server.bots.BotEntry;
import server.agents.capabilities.movement.AgentDownJumpState;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.movement.AgentMovementProfileState;

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

    public static AgentMovementProfile movementProfile(BotEntry entry) {
        return movementProfileState(entry).profile();
    }

    public static AgentMovementProfile movementProfileOrCharacter(BotEntry entry, Character bot) {
        if (entry == null) {
            return AgentMovementProfile.fromCharacter(null);
        }
        AgentMovementProfile profile = movementProfileState(entry).profile();
        return profile == null ? AgentMovementProfile.fromCharacter(bot) : profile;
    }

    public static void setMovementProfile(BotEntry entry, AgentMovementProfile movementProfile) {
        movementProfileState(entry).setProfile(movementProfile);
    }

    public static void refreshMovementProfile(BotEntry entry, Character bot) {
        movementProfileState(entry).refreshFrom(bot);
    }

    public static int moveDirection(BotEntry entry) {
        return movementInputState(entry).moveDirection();
    }

    public static boolean hasMoveDirection(BotEntry entry) {
        return movementInputState(entry).moveDirection() != 0;
    }

    public static void setMoveDirection(BotEntry entry, int moveDirection) {
        movementInputState(entry).setMoveDirection(moveDirection);
    }

    public static void clearMoveDirection(BotEntry entry) {
        movementInputState(entry).clearMoveDirection();
    }

    public static int facingDirection(BotEntry entry) {
        return movementInputState(entry).facingDirection();
    }

    public static int facingDirectionSign(BotEntry entry) {
        return movementInputState(entry).facingDirectionSign();
    }

    public static void setFacingDirection(BotEntry entry, int facingDirection) {
        movementInputState(entry).setFacingDirection(facingDirection);
    }

    public static boolean inAir(BotEntry entry) {
        return entry.movementPhysicsState().inAir();
    }

    public static void setInAir(BotEntry entry, boolean inAir) {
        entry.movementPhysicsState().setInAir(inAir);
    }

    public static boolean grounded(BotEntry entry) {
        return !entry.movementPhysicsState().inAir();
    }

    public static boolean climbing(BotEntry entry) {
        return entry.climbState().climbing();
    }

    public static boolean notClimbing(BotEntry entry) {
        return !entry.climbState().climbing();
    }

    public static boolean downJumpPending(BotEntry entry) {
        return downJumpState(entry).pending();
    }

    public static void setDownJumpPending(BotEntry entry, boolean downJumpPending) {
        downJumpState(entry).setPending(downJumpPending);
    }

    public static boolean crouching(BotEntry entry) {
        return movementInputState(entry).crouching();
    }

    public static void setCrouching(BotEntry entry, boolean crouching) {
        movementInputState(entry).setCrouching(crouching);
    }

    public static boolean hasDownJumpPending(BotEntry entry) {
        return downJumpState(entry).pending();
    }

    public static boolean hasDownJumpGracePeriod(BotEntry entry) {
        return downJumpState(entry).hasGracePeriod();
    }

    public static long downJumpGracePeriodMs(BotEntry entry) {
        return downJumpState(entry).gracePeriodMs();
    }

    public static void setDownJumpGracePeriodMs(BotEntry entry, long downJumpGracePeriodMs) {
        downJumpState(entry).setGracePeriodMs(downJumpGracePeriodMs);
    }

    public static boolean wasMovingX(BotEntry entry) {
        return movementInputState(entry).wasMovingX();
    }

    public static void setWasMovingX(BotEntry entry, boolean wasMovingX) {
        movementInputState(entry).setWasMovingX(wasMovingX);
    }

    public static int movementVelocityX(BotEntry entry) {
        return movementInputState(entry).velocityX();
    }

    public static int movementVelocityY(BotEntry entry) {
        return movementInputState(entry).velocityY();
    }

    public static boolean hasMovementVelocity(BotEntry entry) {
        return movementInputState(entry).hasVelocity();
    }

    public static void setMovementVelocity(BotEntry entry, int velocityX, int velocityY) {
        movementInputState(entry).setVelocity(velocityX, velocityY);
    }

    private static AgentDownJumpState downJumpState(BotEntry entry) {
        return entry.downJumpState();
    }

    private static AgentMovementInputState movementInputState(BotEntry entry) {
        return entry.movementInputState();
    }

    private static AgentMovementProfileState movementProfileState(BotEntry entry) {
        return entry.movementProfileState();
    }

    private static Point position(Character character) {
        return character == null || character.getPosition() == null ? null : new Point(character.getPosition());
    }
}
